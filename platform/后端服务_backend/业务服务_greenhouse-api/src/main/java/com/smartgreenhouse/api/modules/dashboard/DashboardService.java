package com.smartgreenhouse.api.modules.dashboard;

import com.smartgreenhouse.api.integration.cache.JsonCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class DashboardService {

    private final DashboardMapper mapper;
    private final JsonCache cache;
    private final Duration cacheTtl;

    public DashboardService(
            DashboardMapper mapper,
            JsonCache cache,
            @Value("${app.cache.dashboard-ttl-seconds:30}") long cacheTtlSeconds
    ) {
        this.mapper = mapper;
        this.cache = cache;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Transactional(readOnly = true)
    public GovernmentDashboardView governmentDashboard() {
        String cacheKey = "dashboard:government";
        Optional<GovernmentDashboardView> cached =
                cache.get(cacheKey, GovernmentDashboardView.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        int greenhouseCount = mapper.countGreenhouses();
        int deviceCount = mapper.countDevices();
        int onlineDeviceCount = mapper.countOnlineDevices();
        int activeTraceCodeCount = mapper.countActiveTraceCodes();
        int offlineDeviceCount = mapper.countOfflineDevices();
        int pendingChainProofCount = mapper.countPendingChainProofs();
        int repeatScanCount = mapper.countRepeatScans();

        GovernmentDashboardView view = new GovernmentDashboardView(
                greenhouseCount,
                ratio(onlineDeviceCount, deviceCount),
                offlineDeviceCount + pendingChainProofCount + repeatScanCount,
                ratio(activeTraceCodeCount, greenhouseCount),
                offlineDeviceCount,
                pendingChainProofCount,
                repeatScanCount,
                Instant.now()
        );
        cache.put(cacheKey, view, cacheTtl);
        return view;
    }

    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(numerator)
                .divide(
                        BigDecimal.valueOf(denominator),
                        4,
                        RoundingMode.HALF_UP
                );
    }
}
