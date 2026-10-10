package com.smartgreenhouse.api.integration.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演示环境使用的链上网关实现：按调用内容生成确定性交易哈希并递增区块高度。
 * 不连接真实链节点，接口与链上回执结构保持一致，便于后续替换为 FISCO BCOS 实现。
 */
@Component
@ConditionalOnProperty(
        name = "app.chain.mode",
        havingValue = "simulated",
        matchIfMissing = true
)
public class SimulatedChainGateway implements ChainGateway {

    private static final Logger log =
            LoggerFactory.getLogger(SimulatedChainGateway.class);

    private final AtomicLong blockNumber;

    public SimulatedChainGateway(
            @Value("${app.chain.block-base:1284600}") long blockBase
    ) {
        this.blockNumber = new AtomicLong(blockBase);
    }

    @Override
    public ChainReceipt recordEnvironmentRoot(
            String cycleId,
            String rootHash,
            String metadata
    ) {
        return receipt("recordEnvironmentRoot", cycleId, rootHash, metadata);
    }

    @Override
    public ChainReceipt recordCertificate(
            String certificateId,
            String contentHash,
            String status
    ) {
        return receipt("recordCertificate", certificateId, contentHash, status);
    }

    @Override
    public ChainReceipt appendTraceNode(
            String fieldNo,
            int index,
            String nodeHash,
            String previousHash
    ) {
        return receipt(
                "appendTraceNode",
                fieldNo,
                String.valueOf(index),
                nodeHash,
                previousHash
        );
    }

    @Override
    public ChainReceipt registerTraceCode(
            String productCode,
            String signatureHash,
            String batchId
    ) {
        return receipt("registerTraceCode", productCode, signatureHash, batchId);
    }

    @Override
    public ChainReceipt appendScanRecord(
            String productCode,
            String channel,
            String result
    ) {
        return receipt("appendScanRecord", productCode, channel, result);
    }

    private ChainReceipt receipt(String function, String... parts) {
        StringBuilder canonical = new StringBuilder(function);
        for (String part : parts) {
            canonical.append('|').append(Objects.toString(part, ""));
        }
        String transactionHash = "0x" + sha256(canonical.toString());
        long block = blockNumber.incrementAndGet();
        log.debug(
                "模拟链上回执：{} -> {}（区块 {}）",
                function,
                transactionHash,
                block
        );
        return new ChainReceipt(
                transactionHash,
                block,
                "CONFIRMED",
                Instant.now()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
