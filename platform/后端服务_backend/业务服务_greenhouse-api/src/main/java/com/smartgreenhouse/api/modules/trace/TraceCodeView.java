package com.smartgreenhouse.api.modules.trace;

public record TraceCodeView(
        String productCode,
        String fieldNo,
        Long batchId,
        String publicKeyId,
        String status,
        String chainTxHash
) {
}
