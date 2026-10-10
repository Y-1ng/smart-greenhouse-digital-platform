package com.smartgreenhouse.api.integration.external.rest;

import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.SmsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信预警真实适配器：按约定调 POST {base-url}/sms，请求体 {phone, message}。
 */
@Component
@ConditionalOnProperty(name = "app.external.sms.mode", havingValue = "rest")
public class RestSmsClient implements SmsClient {

    private final RestClient client;
    private final String token;

    public RestSmsClient(
            @Value("${app.external.sms.base-url:}") String baseUrl,
            @Value("${app.external.sms.token:}") String token,
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
    public ExternalCallResult sendAlert(String phone, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("message", message);
        try {
            Map<String, Object> payload = client.post()
                    .uri("/sms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) {
                            headers.setBearerAuth(token);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return ExternalCallResult.ok(
                    "预警短信已发送",
                    payload == null ? Map.of() : payload
            );
        } catch (Exception exception) {
            return ExternalCallResult.failure(
                    "短信服务调用失败：" + exception.getMessage()
            );
        }
    }

    @Override
    public String mode() {
        return "rest";
    }
}
