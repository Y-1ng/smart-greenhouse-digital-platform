package com.smartgreenhouse.api.modules.environment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnvironmentThresholdView(
        Long thresholdId,
        String metricCode,
        BigDecimal targetMin,
        BigDecimal targetMax,
        BigDecimal alertMin,
        BigDecimal alertMax,
        String controlAction,
        Boolean enabled,
        LocalDateTime updatedAt
) {
}
