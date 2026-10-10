package com.smartgreenhouse.api.modules.chain;

/**
 * 各类存证的待补偿数量。
 */
public record ChainPendingView(
        int environmentCycle,
        int certificate,
        int traceCode,
        int traceNode,
        int scanRecord,
        int total
) {

    public static ChainPendingView of(
            int environmentCycle,
            int certificate,
            int traceCode,
            int traceNode,
            int scanRecord
    ) {
        return new ChainPendingView(
                environmentCycle,
                certificate,
                traceCode,
                traceNode,
                scanRecord,
                environmentCycle + certificate + traceCode + traceNode + scanRecord
        );
    }
}
