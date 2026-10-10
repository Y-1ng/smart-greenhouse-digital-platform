package com.smartgreenhouse.api.modules.environment;

import java.time.LocalDateTime;

public record EnvironmentCycleView(
        Long cycleId,
        String greenhouseCode,
        LocalDateTime cycleStart,
        LocalDateTime cycleEnd,
        String dataDigest,
        String rootHash,
        String chainStatus,
        String chainTxHash,
        int readingCount
) {
}
