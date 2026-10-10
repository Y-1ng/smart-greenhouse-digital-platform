package com.smartgreenhouse.api.integration.chain;

/**
 * 区块链网关边界，业务模块不直接依赖具体 FISCO BCOS SDK。
 */
public interface ChainGateway {

    ChainReceipt recordEnvironmentRoot(String cycleId, String rootHash, String metadata);

    ChainReceipt recordCertificate(String certificateId, String contentHash, String status);

    ChainReceipt appendTraceNode(
            String fieldNo,
            int index,
            String nodeHash,
            String previousHash
    );

    ChainReceipt registerTraceCode(String productCode, String signatureHash, String batchId);

    ChainReceipt appendScanRecord(String productCode, String channel, String result);
}
