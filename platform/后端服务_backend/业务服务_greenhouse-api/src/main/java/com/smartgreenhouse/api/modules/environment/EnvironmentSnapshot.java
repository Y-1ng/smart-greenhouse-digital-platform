package com.smartgreenhouse.api.modules.environment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record EnvironmentSnapshot(
        String greenhouseId,
        Map<String, BigDecimal> metrics,
        LocalDateTime collectedAt,
        String source,
        String rootHash,
        String chainStatus
) {
}
