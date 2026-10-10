package com.smartgreenhouse.api.integration.external.rest;

import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.WeatherClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 气象服务真实适配器：按约定调 GET {base-url}/forecast。
 * 需要 app.external.weather.mode=rest 并配置 base-url / token 才启用。
 */
@Component
@ConditionalOnProperty(name = "app.external.weather.mode", havingValue = "rest")
public class RestWeatherClient implements WeatherClient {

    private final RestClient client;
    private final String token;

    public RestWeatherClient(
            @Value("${app.external.weather.base-url:}") String baseUrl,
            @Value("${app.external.weather.token:}") String token,
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
    public ExternalCallResult fetchForecast(String greenhouseCode, String location) {
        try {
            Map<String, Object> payload = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/forecast")
                            .queryParam("greenhouseCode", greenhouseCode)
                            .queryParam("location", location == null ? "" : location)
                            .build())
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) {
                            headers.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return ExternalCallResult.ok(
                    "气象预报获取成功",
                    payload == null ? Map.of() : payload
            );
        } catch (Exception exception) {
            return ExternalCallResult.failure(
                    "气象服务调用失败：" + exception.getMessage()
            );
        }
    }

    @Override
    public String mode() {
        return "rest";
    }
}
