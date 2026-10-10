package com.smartgreenhouse.api.integration.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * 链上适配服务健康探针：读取 GET /health，用于界面展示真实链联通情况。
 * 探针失败只返回状态对象，不抛异常，避免影响调用方。
 */
@Component
public class ChainAdapterProbe {

    private static final Logger log =
            LoggerFactory.getLogger(ChainAdapterProbe.class);

    private final RestClient client;
    private final String mode;
    private final String blockPath;

    public ChainAdapterProbe(
            @Value("${app.chain.endpoint}") String endpoint,
            @Value("${app.chain.mode:rest}") String mode,
            @Value("${app.chain.block-path:/chain/block}") String blockPath
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder()
                .baseUrl(endpoint)
                .requestFactory(factory)
                .build();
        this.mode = mode;
        this.blockPath = blockPath == null || blockPath.isBlank()
                ? "/chain/block"
                : blockPath;
    }

    public ChainAdapterStatus probe() {
        if (!"rest".equalsIgnoreCase(mode)) {
            return new ChainAdapterStatus(
                    mode,
                    false,
                    "SIMULATED",
                    null,
                    "当前为本地模拟回执模式，未连接链节点"
            );
        }
        try {
            HealthBody body = client.get()
                    .uri("/health")
                    .retrieve()
                    .body(HealthBody.class);
            if (body == null || body.status() == null) {
                return new ChainAdapterStatus(
                        mode,
                        false,
                        "DOWN",
                        null,
                        "适配服务未返回健康信息"
                );
            }
            return new ChainAdapterStatus(
                    mode,
                    true,
                    body.status(),
                    body.contractAddress(),
                    "链上适配服务可用"
            );
        } catch (Exception exception) {
            log.debug("链上适配服务探针失败：{}", exception.getMessage());
            return new ChainAdapterStatus(
                    mode,
                    false,
                    "DOWN",
                    null,
                    "链上适配服务不可用：" + exception.getMessage()
            );
        }
    }

    record HealthBody(String status, String mode, String contractAddress) {
    }

    /**
     * 读取链上真实区块指标（GET /chain/block）。
     * 适配服务还没升级到这个接口时返回 unavailable，界面据此隐藏区块高度卡片。
     */
    public ChainNetworkStatus probeBlock() {
        if (!"rest".equalsIgnoreCase(mode)) {
            return ChainNetworkStatus.unavailable("本地模拟回执模式，无真实区块高度");
        }
        try {
            BlockBody body = client.get()
                    .uri(blockPath)
                    .retrieve()
                    .body(BlockBody.class);
            if (body == null || body.blockNumber() == null) {
                return ChainNetworkStatus.unavailable("适配服务未返回区块信息");
            }
            return new ChainNetworkStatus(
                    true,
                    body.blockNumber(),
                    body.blockTimestampMs() == null
                            ? null
                            : Instant.ofEpochMilli(body.blockTimestampMs()).toString(),
                    body.avgBlockIntervalMs(),
                    body.sampleSize(),
                    "区块高度来自链上适配服务"
            );
        } catch (Exception exception) {
            log.debug("链上适配服务区块查询失败：{}", exception.getMessage());
            return ChainNetworkStatus.unavailable(
                    "适配服务未提供区块查询接口：" + exception.getMessage()
            );
        }
    }

    record BlockBody(
            Long blockNumber,
            Long blockTimestampMs,
            Long avgBlockIntervalMs,
            Integer sampleSize
    ) {
    }
}
