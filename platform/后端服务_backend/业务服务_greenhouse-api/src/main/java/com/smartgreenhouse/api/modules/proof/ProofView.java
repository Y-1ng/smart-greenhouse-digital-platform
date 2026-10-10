package com.smartgreenhouse.api.modules.proof;

public record ProofView(
        Long batchId,
        String cropName,
        String greenhouseCode,
        Long cycleId,
        String rootHash,
        String chainTxHash,
        String chainStatus,
        String certificateNo,
        String certificateHash,
        String certificateStatus,
        String certificateChainTxHash,
        String productCode,
        String traceStatus,
        String traceChainTxHash,
        String verificationStatus,
        String message
) {
}
