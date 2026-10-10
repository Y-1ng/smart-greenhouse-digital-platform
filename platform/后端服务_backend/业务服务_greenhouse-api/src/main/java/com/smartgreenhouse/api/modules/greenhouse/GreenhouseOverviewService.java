package com.smartgreenhouse.api.modules.greenhouse;

import com.smartgreenhouse.api.integration.cache.JsonCache;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GreenhouseOverviewService {

    private final GreenhouseOverviewMapper mapper;
    private final JsonCache cache;
    private final Duration cacheTtl;

    public GreenhouseOverviewService(
            GreenhouseOverviewMapper mapper,
            JsonCache cache,
            @Value("${app.cache.overview-ttl-seconds:10}") long cacheTtlSeconds
    ) {
        this.mapper = mapper;
        this.cache = cache;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Transactional(readOnly = true)
    public GreenhouseOverview getOverview(String greenhouseCode) {
        String cacheKey = "overview:" + greenhouseCode;
        Optional<GreenhouseOverview> cached =
                cache.get(cacheKey, GreenhouseOverview.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        GreenhouseOverviewMapper.GreenhouseRow greenhouse =
                mapper.findGreenhouseByCode(greenhouseCode);
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }

        GreenhouseOverviewMapper.CycleRow cycle =
                mapper.findLatestCycle(greenhouse.greenhouseId());
        Map<String, BigDecimal> metrics = loadMetrics(
                greenhouse.greenhouseId(),
                cycle
        );
        List<GreenhouseOverview.DeviceSnapshot> devices =
                mapper.findDevices(greenhouse.greenhouseId()).stream()
                        .map(this::toDeviceSnapshot)
                        .toList();
        GreenhouseOverview.CropSnapshot crop = toCropSnapshot(
                mapper.findLatestCrop(greenhouse.greenhouseId())
        );

        GreenhouseOverview overview = new GreenhouseOverview(
                greenhouse.greenhouseCode(),
                greenhouse.greenhouseName(),
                greenhouse.area(),
                greenhouse.status(),
                cycle == null ? null : cycle.cycleId(),
                cycle == null ? null : cycle.rootHash(),
                cycle == null ? null : cycle.chainStatus(),
                metrics,
                crop,
                devices
        );
        cache.put(cacheKey, overview, cacheTtl);
        return overview;
    }

    @Transactional(readOnly = true)
    public List<GreenhouseSummary> listGreenhouses() {
        return mapper.findAllSummaries();
    }

    private Map<String, BigDecimal> loadMetrics(
            Long greenhouseId,
            GreenhouseOverviewMapper.CycleRow cycle
    ) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        if (cycle != null) {
            mapper.findMetrics(cycle.cycleId())
                    .forEach(metric -> metrics.put(
                            metric.metricCode(),
                            metric.metricValue()
                    ));
        }
        // 设备最新遥测优先于周期聚合值，界面展示的是实时读数。
        mapper.findLatestTelemetryMetrics(greenhouseId)
                .forEach(metric -> metrics.put(
                        metric.metricCode(),
                        metric.metricValue()
                ));
        return metrics;
    }

    private GreenhouseOverview.DeviceSnapshot toDeviceSnapshot(
            GreenhouseOverviewMapper.DeviceRow device
    ) {
        return new GreenhouseOverview.DeviceSnapshot(
                device.deviceId(),
                device.deviceCode(),
                device.deviceType(),
                device.onlineStatus(),
                device.operatingState(),
                device.lastSeenAt() == null ? null : device.lastSeenAt().toString()
        );
    }

    private GreenhouseOverview.CropSnapshot toCropSnapshot(
            GreenhouseOverviewMapper.CropRow crop
    ) {
        if (crop == null) {
            return null;
        }
        return new GreenhouseOverview.CropSnapshot(
                crop.batchId(),
                crop.cropName(),
                crop.varietyName(),
                crop.plantingDate(),
                crop.expectedHarvestDate(),
                crop.status()
        );
    }
}
