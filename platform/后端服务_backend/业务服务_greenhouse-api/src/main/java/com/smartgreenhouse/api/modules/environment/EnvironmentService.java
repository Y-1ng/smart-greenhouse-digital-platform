package com.smartgreenhouse.api.modules.environment;

import com.smartgreenhouse.api.integration.chain.ChainInvoker;
import com.smartgreenhouse.api.integration.chain.ChainBusinessKey;
import com.smartgreenhouse.api.integration.chain.ChainReceipt;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EnvironmentService {

    private final EnvironmentMapper mapper;
    private final ChainInvoker chainInvoker;

    public EnvironmentService(
            EnvironmentMapper mapper,
            ChainInvoker chainInvoker
    ) {
        this.mapper = mapper;
        this.chainInvoker = chainInvoker;
    }

    @Transactional(readOnly = true)
    public EnvironmentSnapshot getLatest(String greenhouseCode) {
        EnvironmentMapper.GreenhouseRow greenhouse =
                requireGreenhouse(greenhouseCode);
        EnvironmentMapper.CycleRow cycle =
                mapper.findLatestCycle(greenhouse.greenhouseId());
        if (cycle == null) {
            return new EnvironmentSnapshot(
                    greenhouse.greenhouseCode(),
                    Map.of(),
                    null,
                    "DATABASE",
                    null,
                    null
            );
        }

        List<EnvironmentMapper.ReadingRow> readings =
                mapper.findReadings(cycle.cycleId());
        return new EnvironmentSnapshot(
                greenhouse.greenhouseCode(),
                toMetrics(readings),
                latestCollectedAt(readings),
                "DATABASE",
                cycle.rootHash(),
                cycle.chainStatus()
        );
    }

    @Transactional(readOnly = true)
    public EnvironmentTrend getTrend(String greenhouseCode, int limit) {
        if (limit < 1 || limit > 168) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "趋势点数量必须在 1 到 168 之间"
            );
        }

        EnvironmentMapper.GreenhouseRow greenhouse =
                requireGreenhouse(greenhouseCode);
        List<EnvironmentTrend.TrendPoint> points =
                mapper.findRecentCycles(greenhouse.greenhouseId(), limit)
                        .stream()
                        .map(cycle -> new EnvironmentTrend.TrendPoint(
                                cycle.cycleId(),
                                cycle.cycleStart(),
                                cycle.cycleEnd(),
                                toMetrics(mapper.findReadings(cycle.cycleId())),
                                cycle.rootHash(),
                                cycle.chainStatus()
                        ))
                        .toList();
        return new EnvironmentTrend(greenhouse.greenhouseCode(), points);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public EnvironmentCycleView ingestCycle(
            EnvironmentController.CycleIngestRequest request
    ) {
        EnvironmentMapper.GreenhouseRow greenhouse =
                requireGreenhouse(request.greenhouseCode());
        if (!request.cycleStart().isBefore(request.cycleEnd())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "周期开始时间必须早于结束时间"
            );
        }
        if (mapper.findCycleByWindow(
                greenhouse.greenhouseId(),
                request.cycleStart(),
                request.cycleEnd()
        ) != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该环境周期已存在"
            );
        }

        List<EnvironmentMapper.ReadingInsert> readings =
                request.readings().stream()
                        .map(reading -> new EnvironmentMapper.ReadingInsert(
                                null,
                                requireDeviceId(
                                        greenhouse.greenhouseId(),
                                        reading.deviceCode()
                                ),
                                EnvironmentMetrics.normalize(reading.metricCode()),
                                reading.value(),
                                reading.unit(),
                                reading.collectedAt()
                        ))
                        .sorted(Comparator
                                .comparing(EnvironmentMapper.ReadingInsert::metricCode)
                                .thenComparing(EnvironmentMapper.ReadingInsert::deviceId)
                                .thenComparing(EnvironmentMapper.ReadingInsert::collectedAt))
                        .toList();
        requireCompleteMetrics(
                readings.stream()
                        .map(EnvironmentMapper.ReadingInsert::metricCode)
                        .toList(),
                ErrorCode.BAD_REQUEST,
                "环境周期"
        );
        String dataDigest = sha256(canonical(request, readings));
        String rootHash = sha256(
                dataDigest + ":" + previousRootHash(greenhouse.greenhouseId())
        );

        mapper.insertCycle(new EnvironmentMapper.CycleInsert(
                greenhouse.greenhouseId(),
                request.cycleStart(),
                request.cycleEnd(),
                dataDigest,
                rootHash
        ));
        EnvironmentMapper.CycleRow cycle = mapper.findCycleByWindow(
                greenhouse.greenhouseId(),
                request.cycleStart(),
                request.cycleEnd()
        );
        for (EnvironmentMapper.ReadingInsert reading : readings) {
            mapper.insertReading(new EnvironmentMapper.ReadingInsert(
                    cycle.cycleId(),
                    reading.deviceId(),
                    reading.metricCode(),
                    reading.metricValue(),
                    reading.unit(),
                    reading.collectedAt()
            ));
        }

        ChainReceipt receipt = chainInvoker.recordEnvironmentRoot(
                ChainBusinessKey.environmentCycle(
                        greenhouse.greenhouseCode(),
                        cycle.cycleStart(),
                        cycle.cycleEnd()
                ),
                rootHash,
                dataDigest
        );
        mapper.updateChainReceipt(
                cycle.cycleId(),
                receipt.transactionHash(),
                receipt.status()
        );

        return new EnvironmentCycleView(
                cycle.cycleId(),
                greenhouse.greenhouseCode(),
                cycle.cycleStart(),
                cycle.cycleEnd(),
                dataDigest,
                rootHash,
                receipt.status(),
                receipt.transactionHash(),
                readings.size()
        );
    }

    @Transactional
    public EnvironmentCycleView attestFromTelemetry(String greenhouseCode) {
        EnvironmentMapper.GreenhouseRow greenhouse =
                requireGreenhouse(greenhouseCode);
        EnvironmentMapper.CycleRow latestCycle =
                mapper.findLatestCycle(greenhouse.greenhouseId());
        LocalDateTime after = latestCycle == null
                ? LocalDateTime.now().minusHours(24)
                : latestCycle.cycleEnd();
        List<EnvironmentMapper.TelemetryReadingRow> telemetry =
                mapper.findTelemetryReadings(
                        greenhouse.greenhouseId(),
                        after,
                        LocalDateTime.now().plusSeconds(1)
                );
        if (telemetry.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "当前没有新的遥测数据，暂时不能生成环境周期"
            );
        }

        List<EnvironmentController.ReadingRequest> readings =
                telemetry.stream()
                        .collect(Collectors.groupingBy(
                                row -> EnvironmentMetrics.normalize(row.metricCode()),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ))
                        .values()
                        .stream()
                        .map(this::aggregateReading)
                        .sorted(Comparator.comparing(
                                EnvironmentController.ReadingRequest::metricCode
                        ))
                        .toList();
        requireCompleteMetrics(
                readings.stream()
                        .map(EnvironmentController.ReadingRequest::metricCode)
                        .toList(),
                ErrorCode.CONFLICT,
                "本次遥测"
        );
        LocalDateTime cycleStart = latestCycle == null
                ? telemetry.get(0).collectedAt().minusSeconds(1)
                : latestCycle.cycleEnd();
        LocalDateTime cycleEnd = telemetry.get(telemetry.size() - 1).collectedAt();
        if (!cycleStart.isBefore(cycleEnd)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "当前遥测尚未形成新的时间窗口"
            );
        }
        return ingestCycle(new EnvironmentController.CycleIngestRequest(
                greenhouse.greenhouseCode(),
                cycleStart,
                cycleEnd,
                readings
        ));
    }

    private EnvironmentController.ReadingRequest aggregateReading(
            List<EnvironmentMapper.TelemetryReadingRow> readings
    ) {
        EnvironmentMapper.TelemetryReadingRow latest =
                readings.get(readings.size() - 1);
        BigDecimal average = readings.stream()
                .map(EnvironmentMapper.TelemetryReadingRow::metricValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(
                        BigDecimal.valueOf(readings.size()),
                        4,
                        RoundingMode.HALF_UP
                );
        return new EnvironmentController.ReadingRequest(
                latest.deviceCode(),
                EnvironmentMetrics.normalize(latest.metricCode()),
                average,
                latest.unit(),
                latest.collectedAt()
        );
    }

    private void requireCompleteMetrics(
            List<String> metricCodes,
            ErrorCode errorCode,
            String scene
    ) {
        List<String> missing = EnvironmentMetrics.missingCodes(metricCodes);
        if (missing.isEmpty()) {
            return;
        }
        throw new BusinessException(
                errorCode,
                scene + "缺少" + EnvironmentMetrics.labelsOf(missing)
                        + "读数；环境周期存证需要温度、湿度、光照、土壤水分四项指标全部到齐"
        );
    }

    private EnvironmentMapper.GreenhouseRow requireGreenhouse(
            String greenhouseCode
    ) {
        EnvironmentMapper.GreenhouseRow greenhouse =
                mapper.findGreenhouseByCode(greenhouseCode);
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        return greenhouse;
    }

    private Map<String, BigDecimal> toMetrics(
            List<EnvironmentMapper.ReadingRow> readings
    ) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        readings.forEach(reading -> metrics.put(
                reading.metricCode(),
                reading.metricValue()
        ));
        return metrics;
    }

    private LocalDateTime latestCollectedAt(
            List<EnvironmentMapper.ReadingRow> readings
    ) {
        return readings.stream()
                .map(EnvironmentMapper.ReadingRow::collectedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private Long requireDeviceId(Long greenhouseId, String deviceCode) {
        Long deviceId = mapper.findDeviceId(greenhouseId, deviceCode);
        if (deviceId == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "设备不存在或不属于该温室: " + deviceCode
            );
        }
        return deviceId;
    }

    private String previousRootHash(Long greenhouseId) {
        EnvironmentMapper.CycleRow cycle =
                mapper.findLatestCycle(greenhouseId);
        return cycle == null ? "GENESIS" : cycle.rootHash();
    }

    private String canonical(
            EnvironmentController.CycleIngestRequest request,
            List<EnvironmentMapper.ReadingInsert> readings
    ) {
        StringBuilder value = new StringBuilder()
                .append(request.greenhouseCode())
                .append('|')
                .append(request.cycleStart())
                .append('|')
                .append(request.cycleEnd());
        readings.forEach(reading -> value
                .append('|')
                .append(reading.deviceId())
                .append(':')
                .append(reading.metricCode())
                .append(':')
                .append(reading.metricValue().stripTrailingZeros())
                .append(':')
                .append(reading.unit())
                .append(':')
                .append(reading.collectedAt()));
        return value.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "环境摘要生成失败"
            );
        }
    }
}
