package com.smartgreenhouse.iot.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.business.BusinessApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 设备状态上行：解析 iot/{温室}/{设备}/status 报文并转发业务服务。
 */
@Service
public class DeviceStatusIngestService {

    private static final Logger log =
            LoggerFactory.getLogger(DeviceStatusIngestService.class);

    private final ObjectMapper objectMapper;
    private final BusinessApiClient businessApiClient;

    public DeviceStatusIngestService(
            ObjectMapper objectMapper,
            BusinessApiClient businessApiClient
    ) {
        this.objectMapper = objectMapper;
        this.businessApiClient = businessApiClient;
    }

    public void accept(String topic, byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String greenhouseCode = MqttTopics.part(topic, 1);
            String deviceCode = MqttTopics.part(topic, 2);
            JsonNode onlineNode = root.path("online");
            boolean online = onlineNode.isMissingNode()
                    || onlineNode.asBoolean(true);

            businessApiClient.forwardDeviceStatus(
                    new BusinessApiClient.DeviceStatusMessage(
                            greenhouseCode,
                            deviceCode,
                            online,
                            text(root, "lastSeenAt"),
                            text(root, "message")
                    )
            );
            log.info(
                    "Forwarded device status: {}/{} online={}",
                    greenhouseCode,
                    deviceCode,
                    online
            );
        } catch (Exception exception) {
            log.error("Failed to process device status from {}", topic, exception);
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }
}
