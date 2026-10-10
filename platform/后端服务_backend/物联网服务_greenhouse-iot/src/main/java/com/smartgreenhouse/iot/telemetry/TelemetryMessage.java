package com.smartgreenhouse.iot.telemetry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TelemetryMessage(
        String greenhouseCode,
        String deviceCode,
        String metricCode,
        BigDecimal value,
        String unit,
        LocalDateTime collectedAt,
        String topic
) {
}
