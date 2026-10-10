package com.smartgreenhouse.api.integration.chain;

/**
 * 链上运行指标（只读）：区块高度、最新区块时间与近期平均出块间隔。
 *
 * <p>available=false 表示链上适配服务没有提供区块查询接口，或本次查询失败。
 * 界面据此隐藏区块高度，而不是拿模拟值顶上——测不到就如实说测不到。
 */
public record ChainNetworkStatus(
        boolean available,
        Long blockNumber,
        String blockTimestamp,
        Long avgBlockIntervalMs,
        Integer sampleSize,
        String message
) {

    public static ChainNetworkStatus unavailable(String message) {
        return new ChainNetworkStatus(false, null, null, null, null, message);
    }
}
