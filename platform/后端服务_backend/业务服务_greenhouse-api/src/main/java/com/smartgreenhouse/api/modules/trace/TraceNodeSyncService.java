package com.smartgreenhouse.api.modules.trace;

import com.smartgreenhouse.api.integration.chain.ChainInvoker;
import com.smartgreenhouse.api.integration.chain.ChainReceipt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * 追溯节点补链：按节点下标顺序把待上链节点写入合约。
 *
 * 合约的 appendTraceNode 按「田块 + 下标」定位节点：
 * 下标小于链上已有长度时要求摘要一致（幂等重放，拒绝覆盖历史），
 * 下标等于长度时要求 previousHash 等于链上链尾（顺序追加）。
 * 因此补偿时必须严格从最早的待上链节点开始，跳序会被合约拒绝；
 * 这里每批只取「前序都已上链」的节点，推送成功后重新查询，直到没有可提交的节点。
 */
@Service
public class TraceNodeSyncService {

    private final TraceMapper mapper;
    private final ChainInvoker chainInvoker;

    public TraceNodeSyncService(TraceMapper mapper, ChainInvoker chainInvoker) {
        this.mapper = mapper;
        this.chainInvoker = chainInvoker;
    }

    /**
     * 补链指定田块的待上链节点，返回成功条数。
     */
    public TraceNodeSyncReport syncField(String fieldNo, int limit) {
        return sync(remaining -> mapper.findPendingNodesByField(
                fieldNo,
                remaining
        ), limit);
    }

    /**
     * 补偿任务使用：所有田块各取最早的一个待上链节点，循环推进到最后。
     */
    public TraceNodeSyncReport syncPending(int limit) {
        return sync(mapper::findPendingNodes, limit);
    }

    private TraceNodeSyncReport sync(
            IntFunction<List<TraceMapper.PendingNode>> query,
            int limit
    ) {
        List<TraceNodeSyncReport.Confirmed> confirmed = new ArrayList<>();
        while (confirmed.size() < limit) {
            List<TraceMapper.PendingNode> candidates =
                    query.apply(limit - confirmed.size());
            if (candidates.isEmpty()) {
                break;
            }
            for (TraceMapper.PendingNode node : candidates) {
                ChainReceipt receipt = chainInvoker.appendTraceNode(
                        node.fieldNo(),
                        node.index(),
                        node.payloadHash(),
                        node.previousHash()
                );
                if (receipt.transactionHash() == null) {
                    // 链上链尾与本地指针不一致时继续提交只会连续失败，本轮到此为止。
                    return new TraceNodeSyncReport(confirmed, 1);
                }
                mapper.updateNodeChainTxHash(
                        node.nodeId(),
                        receipt.transactionHash()
                );
                confirmed.add(new TraceNodeSyncReport.Confirmed(
                        node.nodeId(),
                        node.fieldNo(),
                        receipt.transactionHash(),
                        receipt.blockNumber()
                ));
            }
        }
        return new TraceNodeSyncReport(confirmed, 0);
    }
}
