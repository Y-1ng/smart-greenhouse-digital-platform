package com.smartgreenhouse.api.modules.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record GovernmentDashboardView(
        int greenhouseCount,
        BigDecimal deviceOnlineRate,
        int activeAlerts,
        BigDecimal traceabilityCoverage,
        int offlineDeviceCount,
        int pendingChainProofCount,
        int repeatScanCount,
        Instant generatedAt
) {
}
