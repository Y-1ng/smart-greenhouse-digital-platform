package com.smartgreenhouse.api.modules.trace;

import java.util.List;

/**
 * 追溯节点补链结果。
 */
public record TraceNodeSyncReport(
        List<Confirmed> confirmed,
        int failed
) {

    public record Confirmed(
            Long nodeId,
            String fieldNo,
            String transactionHash,
            long blockNumber
    ) {
    }

    public int total() {
        return confirmed.size() + failed;
    }
}
