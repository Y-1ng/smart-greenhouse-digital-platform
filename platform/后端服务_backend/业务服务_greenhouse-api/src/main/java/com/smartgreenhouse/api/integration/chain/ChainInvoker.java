package com.smartgreenhouse.api.integration.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 链上调用包装：链上失败不阻断业务，返回待补偿回执并记录告警日志。
 */
@Component
public class ChainInvoker {

    private static final Logger log = LoggerFactory.getLogger(ChainInvoker.class);

    private final ChainGateway gateway;

    public ChainInvoker(ChainGateway gateway) {
        this.gateway = gateway;
    }

    public ChainReceipt recordEnvironmentRoot(
            String cycleId,
            String rootHash,
            String metadata
    ) {
        return invoke(
                () -> gateway.recordEnvironmentRoot(cycleId, rootHash, metadata),
                "环境周期存证"
        );
    }

    public ChainReceipt recordCertificate(
            String certificateId,
            String contentHash,
            String status
    ) {
        return invoke(
                () -> gateway.recordCertificate(
                        certificateId,
                        contentHash,
                        status
                ),
                "合格证存证"
        );
    }

    public ChainReceipt registerTraceCode(
            String productCode,
            String signatureHash,
            String batchId
    ) {
        return invoke(
                () -> gateway.registerTraceCode(
                        productCode,
                        signatureHash,
                        batchId
                ),
                "一物一码存证"
        );
    }

    public ChainReceipt appendTraceNode(
            String fieldNo,
            int index,
            String nodeHash,
            String previousHash
    ) {
        return invoke(
                () -> gateway.appendTraceNode(
                        fieldNo,
                        index,
                        nodeHash,
                        previousHash
                ),
                "追溯节点存证"
        );
    }

    public ChainReceipt appendScanRecord(
            String productCode,
            String channel,
            String result
    ) {
        return invoke(
                () -> gateway.appendScanRecord(productCode, channel, result),
                "扫码记录存证"
        );
    }

    private ChainReceipt invoke(
            Supplier<ChainReceipt> call,
            String action
    ) {
        try {
            return call.get();
        } catch (Exception exception) {
            log.warn(
                    "{}失败，保留待补偿状态：{}",
                    action,
                    exception.getMessage()
            );
            return ChainReceipt.pending();
        }
    }
}
