package com.smartgreenhouse.api.modules.telemetry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IotTelemetryView(
        Long telemetryId,
        String greenhouseCode,
        String deviceCode,
        String metricCode,
        BigDecimal value,
        String unit,
        LocalDateTime collectedAt,
        String topic
) {
}
