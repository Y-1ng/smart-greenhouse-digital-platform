package com.smartgreenhouse.api.modules.trace;

/**
 * 追加追溯节点的结果：节点摘要、链上回执与指针链状态。
 */
public record TraceNodeView(
        String fieldNo,
        Long nodeId,
        String stageType,
        String previousHash,
        String payloadHash,
        String chainTxHash,
        boolean pointerChainValid,
        int nodeCount,
        String message
) {
}
