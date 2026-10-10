package com.smartgreenhouse.api.integration.chain;

/**
 * 链上适配服务联通状态。
 *
 * @param mode            当前链上网关模式（rest / simulated）
 * @param reachable       适配服务是否可达
 * @param status          适配服务上报的状态（UP / DOWN）
 * @param contractAddress 适配服务当前使用的合约地址
 * @param message         面向界面的说明文字
 */
public record ChainAdapterStatus(
        String mode,
        boolean reachable,
        String status,
        String contractAddress,
        String message
) {
}
