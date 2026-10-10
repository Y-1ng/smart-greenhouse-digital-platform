package com.smartgreenhouse.api.modules.telemetry;

import com.smartgreenhouse.api.modules.alert.AlertService;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class IotTelemetryService {

    private final IotTelemetryMapper mapper;
    private final AlertService alertService;

    public IotTelemetryService(
            IotTelemetryMapper mapper,
            AlertService alertService
    ) {
        this.mapper = mapper;
        this.alertService = alertService;
    }

    @Transactional
    public IotTelemetryView ingest(
            IotTelemetryController.IngestRequest request
    ) {
        IotTelemetryMapper.GreenhouseRow greenhouse =
                mapper.findGreenhouseByCode(request.greenhouseCode());
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        Long deviceId = mapper.findDeviceId(
                greenhouse.greenhouseId(),
                request.deviceCode()
        );
        if (deviceId == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "设备不存在或不属于该温室"
            );
        }

        mapper.insert(new IotTelemetryMapper.TelemetryInsert(
                greenhouse.greenhouseId(),
                deviceId,
                request.metricCode(),
                request.value(),
                request.unit(),
                request.collectedAt(),
                request.topic()
        ));
        // 遥测到达即认为设备在线，顺带刷新最后在线时间。
        mapper.touchDevice(
                greenhouse.greenhouseId(),
                request.deviceCode(),
                request.collectedAt()
        );
        alertService.evaluate(
                greenhouse.greenhouseId(),
                request.metricCode(),
                request.value(),
                request.collectedAt()
        );
        return mapper.findLatest(
                        greenhouse.greenhouseCode(),
                        request.metricCode()
                )
                .stream()
                .findFirst()
                .map(this::toView)
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<IotTelemetryView> latest(
            String greenhouseCode,
            String metricCode
    ) {
        if (mapper.findGreenhouseByCode(greenhouseCode) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        return mapper.findLatest(greenhouseCode, metricCode)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public IotTelemetryHistory history(
            String greenhouseCode,
            int minutes,
            int bucketSeconds
    ) {
        if (minutes < 1 || minutes > 1440) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "采样时间窗口必须在 1 到 1440 分钟之间"
            );
        }
        if (bucketSeconds < 5 || bucketSeconds > 300) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "采样粒度必须在 5 到 300 秒之间"
            );
        }
        if (mapper.findGreenhouseByCode(greenhouseCode) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }

        LocalDateTime until = LocalDateTime.now().withNano(0).plusSeconds(1);
        LocalDateTime after = until.minusMinutes(minutes).minusSeconds(1);
        Map<Long, SampleAccumulator> buckets = new TreeMap<>();
        for (IotTelemetryMapper.TelemetryRow row :
                mapper.findReadingsBetween(greenhouseCode, after, until)) {
            long collectedAt = row.collectedAt().toEpochSecond(ZoneOffset.UTC);
            long bucketStart =
                    Math.floorDiv(collectedAt, bucketSeconds) * bucketSeconds;
            buckets.computeIfAbsent(
                    bucketStart,
                    ignored -> new SampleAccumulator()
            ).add(row.metricCode(), row.metricValue());
        }

        List<IotTelemetryHistory.Sample> samples = buckets.entrySet().stream()
                .map(entry -> new IotTelemetryHistory.Sample(
                        LocalDateTime.ofEpochSecond(
                                entry.getKey(),
                                0,
                                ZoneOffset.UTC
                        ),
                        entry.getValue().averages()
                ))
                .toList();
        return new IotTelemetryHistory(
                greenhouseCode,
                minutes,
                bucketSeconds,
                samples
        );
    }

    private IotTelemetryView toView(IotTelemetryMapper.TelemetryRow row) {
        return new IotTelemetryView(
                row.telemetryId(),
                row.greenhouseCode(),
                row.deviceCode(),
                row.metricCode(),
                row.metricValue(),
                row.unit(),
                row.collectedAt(),
                row.rawTopic()
        );
    }

    private static final class SampleAccumulator {

        private final Map<String, BigDecimal> totals = new LinkedHashMap<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();

        void add(String metricCode, BigDecimal value) {
            totals.merge(metricCode, value, BigDecimal::add);
            counts.merge(metricCode, 1, Integer::sum);
        }

        Map<String, BigDecimal> averages() {
            Map<String, BigDecimal> averages = new LinkedHashMap<>();
            totals.forEach((metricCode, total) -> averages.put(
                    metricCode,
                    total.divide(
                            BigDecimal.valueOf(counts.get(metricCode)),
                            4,
                            RoundingMode.HALF_UP
                    )
            ));
            return averages;
        }
    }
}
