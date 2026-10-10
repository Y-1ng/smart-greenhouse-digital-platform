package com.smartgreenhouse.api.modules.telemetry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record IotTelemetryHistory(
        String greenhouseCode,
        int minutes,
        int bucketSeconds,
        List<Sample> samples
) {

    public record Sample(
            LocalDateTime at,
            Map<String, BigDecimal> metrics
    ) {
    }
}
