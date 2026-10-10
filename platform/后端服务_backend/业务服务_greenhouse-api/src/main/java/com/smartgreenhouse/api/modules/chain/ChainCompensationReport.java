package com.smartgreenhouse.api.modules.chain;

import java.util.List;

/**
 * 一次补偿执行的结果：尝试条数、成功条数、失败条数与明细。
 */
public record ChainCompensationReport(
        int attempted,
        int confirmed,
        int failed,
        List<Entry> entries,
        List<String> failures
) {

    public record Entry(
            String type,
            String businessId,
            String transactionHash,
            long blockNumber
    ) {
    }
}
