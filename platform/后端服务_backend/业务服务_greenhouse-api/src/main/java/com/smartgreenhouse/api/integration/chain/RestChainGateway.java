package com.smartgreenhouse.api.integration.chain;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过链上适配服务调用 FISCO BCOS 的网关实现。
 * 适配服务负责交易签名与合约调用，本类只发送方法名与参数并解析回执。
 */
@Component
@ConditionalOnProperty(name = "app.chain.mode", havingValue = "rest")
public class RestChainGateway implements ChainGateway {

    private final RestClient restClient;
    private final String adapterPath;
    private final ChainBusinessNamespace namespace;

    // 类中存在供测试使用的构造器，这里显式标注注入入口，避免 Spring 找不到构造器。
    @Autowired
    public RestChainGateway(
            @Value("${app.chain.endpoint}") String endpoint,
            @Value("${app.chain.adapter-path:/chain/adapter}") String adapterPath,
            @Value("${app.chain.timeout-ms:3000}") int timeoutMs,
            ChainBusinessNamespace namespace
    ) {
        this(buildClient(endpoint, timeoutMs), adapterPath, namespace);
    }

    RestChainGateway(RestClient restClient, String adapterPath) {
        this(
                restClient,
                adapterPath,
                new ChainBusinessNamespace("", "none")
        );
    }

    RestChainGateway(
            RestClient restClient,
            String adapterPath,
            ChainBusinessNamespace namespace
    ) {
        this.restClient = restClient;
        this.adapterPath = adapterPath;
        this.namespace = namespace;
    }

    private static RestClient buildClient(String endpoint, int timeoutMs) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        // 连接超时单独收紧：适配服务不可达时快速失败，避免占用调度线程
        factory.setConnectTimeout(Math.min(timeoutMs, 3000));
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .baseUrl(endpoint)
                .requestFactory(factory)
                .build();
    }

    @Override
    public ChainReceipt recordEnvironmentRoot(
            String cycleId,
            String rootHash,
            String metadata
    ) {
        return call("recordEnvironmentRoot", Map.of(
                "cycleId", namespace.qualify(cycleId),
                "rootHash", rootHash,
                "metadata", metadata == null ? "" : metadata
        ));
    }

    @Override
    public ChainReceipt recordCertificate(
            String certificateId,
            String contentHash,
            String status
    ) {
        return call("recordCertificate", Map.of(
                "certificateId", namespace.qualify(certificateId),
                "contentHash", contentHash,
                "status", status
        ));
    }

    @Override
    public ChainReceipt appendTraceNode(
            String fieldNo,
            int index,
            String nodeHash,
            String previousHash
    ) {
        return call("appendTraceNode", Map.of(
                "fieldNo", namespace.qualify(fieldNo),
                "index", String.valueOf(index),
                "nodeHash", nodeHash,
                "previousHash", previousHash
        ));
    }

    @Override
    public ChainReceipt registerTraceCode(
            String productCode,
            String signatureHash,
            String batchId
    ) {
        return call("registerTraceCode", Map.of(
                "productCode", namespace.qualify(productCode),
                "signatureHash", signatureHash,
                "batchId", batchId
        ));
    }

    @Override
    public ChainReceipt appendScanRecord(
            String productCode,
            String channel,
            String result
    ) {
        return call("appendScanRecord", Map.of(
                "productCode", namespace.qualify(productCode),
                "channel", channel,
                "result", result
        ));
    }

    private ChainReceipt call(String method, Map<String, String> arguments) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", method);
        payload.put("arguments", arguments);
        try {
            AdapterResponse response = restClient.post()
                    .uri(adapterPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(AdapterResponse.class);
            if (response == null
                    || response.transactionHash() == null
                    || response.transactionHash().isBlank()) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "链上适配服务未返回交易哈希"
                );
            }
            return new ChainReceipt(
                    response.transactionHash(),
                    response.blockNumber(),
                    response.status() == null ? "CONFIRMED" : response.status(),
                    Instant.now()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "链上适配服务调用失败：" + exception.getMessage()
            );
        }
    }

    record AdapterResponse(
            String transactionHash,
            long blockNumber,
            String status
    ) {
    }
}
