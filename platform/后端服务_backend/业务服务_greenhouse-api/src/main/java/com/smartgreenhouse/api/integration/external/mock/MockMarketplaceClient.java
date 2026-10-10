package com.smartgreenhouse.api.integration.external.mock;

import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.MarketplaceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 京东农场等产销平台模拟实现：返回可同步的批次摘要，不调用真实开放平台。
 */
@Component
@ConditionalOnProperty(
        name = "app.external.marketplace.mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockMarketplaceClient implements MarketplaceClient {

    @Override
    public ExternalCallResult syncProducts(String greenhouseCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("greenhouseCode", greenhouseCode);
        payload.put("syncedAt", LocalDateTime.now().withNano(0).toString());
        payload.put("batches", List.of(
                Map.of("batchCode", greenhouseCode + "-B01", "status", "ON_SALE"),
                Map.of("batchCode", greenhouseCode + "-B02", "status", "PENDING")
        ));
        payload.put("syncedCount", 2);
        return ExternalCallResult.ok("批次信息同步成功（模拟）", payload);
    }

    @Override
    public String mode() {
        return "mock";
    }
}
