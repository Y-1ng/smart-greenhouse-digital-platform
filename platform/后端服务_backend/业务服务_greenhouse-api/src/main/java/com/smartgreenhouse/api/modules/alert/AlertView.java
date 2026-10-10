package com.smartgreenhouse.api.modules.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlertView(
        Long alertId,
        String greenhouseCode,
        String metricCode,
        BigDecimal metricValue,
        String alertLevel,
        BigDecimal alertMin,
        BigDecimal alertMax,
        String message,
        String status,
        LocalDateTime createdAt,
        LocalDateTime acknowledgedAt,
        String acknowledgedBy
) {
}
