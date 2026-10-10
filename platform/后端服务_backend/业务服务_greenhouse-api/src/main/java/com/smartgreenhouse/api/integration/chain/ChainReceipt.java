package com.smartgreenhouse.api.integration.chain;

import java.time.Instant;

public record ChainReceipt(
        String transactionHash,
        long blockNumber,
        String status,
        Instant confirmedAt
) {

    /**
     * 链上调用失败时使用的占位回执，业务侧据此保留待补偿状态。
     */
    public static ChainReceipt pending() {
        return new ChainReceipt(null, 0L, "PENDING", null);
    }
}
