package com.smartgreenhouse.iot.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.telemetry.TelemetryMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 物联网服务调用业务服务的统一客户端：遥测上报、设备状态上报、
 * 拉取待下发指令、指令下发标记与设备回执转发都走这里，避免各处重复拼装请求。
 */
@Component
public class BusinessApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final HttpHeaders headers;

    public BusinessApiClient(
            @Value("${app.business-api.base-url}") String baseUrl,
            @Value("${app.business-api.username}") String username,
            @Value("${app.business-api.password}") String password,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        // 用容器内的 ObjectMapper 替换默认转换器：业务服务的响应字段比
        // 本服务需要的多，默认配置会因未知字段抛异常。
        this.restTemplate.getMessageConverters().removeIf(
                MappingJackson2HttpMessageConverter.class::isInstance
        );
        this.restTemplate.getMessageConverters().add(
                new MappingJackson2HttpMessageConverter(objectMapper)
        );
        this.headers = new HttpHeaders();
        this.headers.setContentType(MediaType.APPLICATION_JSON);
        this.headers.setBasicAuth(username, password);
    }

    public void forwardTelemetry(TelemetryMessage message) {
        post("/api/iot/telemetry/readings", message);
    }

    public void forwardDeviceStatus(DeviceStatusMessage message) {
        post("/api/iot/devices/status", message);
    }

    public void forwardCommandAck(CommandAckMessage message) {
        post(
                "/api/control/commands/" + message.commandNo() + "/ack",
                Map.of(
                        "success", message.success(),
                        "message", message.message() == null
                                ? ""
                                : message.message(),
                        "executedAt", message.executedAt() == null
                                ? ""
                                : message.executedAt()
                )
        );
    }

    public List<DeviceCommand> fetchPendingCommands(int limit) {
        String body = restTemplate.exchange(
                baseUrl + "/api/control/commands/pending?limit=" + limit,
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        ).getBody();
        return parsePendingCommands(body);
    }

    /**
     * 解析业务服务返回的标准响应体，保留包可见便于契约测试。
     */
    List<DeviceCommand> parsePendingCommands(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            ApiEnvelope<List<DeviceCommand>> envelope = objectMapper.readValue(
                    body,
                    new TypeReference<>() {
                    }
            );
            return envelope.data() == null
                    ? List.of()
                    : envelope.data();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "待下发指令响应无法解析",
                    exception
            );
        }
    }

    public void markDispatched(String commandNo) {
        post("/api/control/commands/" + commandNo + "/dispatch", Map.of());
    }

    private void post(String path, Object body) {
        restTemplate.postForObject(
                baseUrl + path,
                new HttpEntity<>(body, headers),
                Void.class
        );
    }

    record ApiEnvelope<T>(String code, String message, T data) {
    }

    public record DeviceCommand(
            String commandNo,
            String greenhouseCode,
            String deviceCode,
            String commandType,
            Map<String, Object> parameters,
            int timeoutSeconds
    ) {
    }

    public record DeviceStatusMessage(
            String greenhouseCode,
            String deviceCode,
            boolean online,
            String lastSeenAt,
            String message
    ) {
    }

    public record CommandAckMessage(
            String commandNo,
            boolean success,
            String message,
            String executedAt
    ) {
    }
}
