package com.smartgreenhouse.api.modules.environment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record EnvironmentTrend(
        String greenhouseCode,
        List<TrendPoint> points
) {

    public record TrendPoint(
            Long cycleId,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd,
            Map<String, BigDecimal> metrics,
            String rootHash,
            String chainStatus
    ) {
    }
}
