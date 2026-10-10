package com.smartgreenhouse.api.modules.chain;

import com.smartgreenhouse.api.integration.chain.ChainAdapterStatus;
import com.smartgreenhouse.api.integration.chain.ChainNetworkStatus;

/**
 * 链上运行状态：适配服务联通情况 + 存证规模 + 真实区块指标 + 各类待补偿数量。
 *
 * @param proofCount 全平台存证记录总数（真实计数，来自五类存证表）
 * @param network    区块高度等链上指标，适配服务未提供时 available=false
 */
public record ChainStatusView(
        ChainAdapterStatus adapter,
        ChainPendingView pending,
        int proofCount,
        ChainNetworkStatus network
) {
}
