package com.smartgreenhouse.api.integration.external.rest;

import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.MarketplaceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 京东农场等产销平台真实适配器：按约定调 POST {base-url}/products/sync。
 */
@Component
@ConditionalOnProperty(name = "app.external.marketplace.mode", havingValue = "rest")
public class RestMarketplaceClient implements MarketplaceClient {

    private final RestClient client;
    private final String token;

    public RestMarketplaceClient(
            @Value("${app.external.marketplace.base-url:}") String baseUrl,
            @Value("${app.external.marketplace.token:}") String token,
            @Value("${app.external.timeout-ms:3000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeoutMs, 3000));
        factory.setReadTimeout(timeoutMs);
        this.client = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank()
                        ? "http://localhost"
                        : baseUrl)
                .requestFactory(factory)
                .build();
        this.token = token;
    }

    @Override
    public ExternalCallResult syncProducts(String greenhouseCode) {
        try {
            Map<String, Object> payload = client.post()
                    .uri("/products/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) {
                            headers.setBearerAuth(token);
                        }
                    })
                    .body(Map.of("greenhouseCode", greenhouseCode))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return ExternalCallResult.ok(
                    "批次信息同步成功",
                    payload == null ? Map.of() : payload
            );
        } catch (Exception exception) {
            return ExternalCallResult.failure(
                    "产销平台调用失败：" + exception.getMessage()
            );
        }
    }

    @Override
    public String mode() {
        return "rest";
    }
}
