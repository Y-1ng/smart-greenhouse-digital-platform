package com.smartgreenhouse.api.modules.trace;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TraceView(
        String productCode,
        boolean found,
        String verificationStatus,
        Long batchId,
        String certificateNo,
        String certificateStatus,
        String fieldNo,
        String publicKeyId,
        String rootHash,
        EnvironmentView environment,
        boolean pointerChainValid,
        List<TraceStageView> stages,
        String message
) {

    /** 该溯源码所属批次对应的环境周期存证。 */
    public record EnvironmentView(
            Long cycleId,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd,
            String chainStatus,
            String dataDigest,
            LocalDateTime collectedAt,
            List<EnvironmentMetricView> metrics
    ) {
    }

    public record EnvironmentMetricView(
            String metricCode,
            BigDecimal value,
            String unit,
            LocalDateTime collectedAt
    ) {
    }

    public record TraceStageView(
            Long nodeId,
            String stageType,
            String previousHash,
            String payloadHash,
            String chainTxHash,
            LocalDateTime createdAt
    ) {
    }
}
