package com.smartgreenhouse.iot.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.business.BusinessApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 设备指令回执上行：解析 iot/{温室}/{设备}/ack 报文并转发业务服务。
 */
@Service
public class CommandAckIngestService {

    private static final Logger log =
            LoggerFactory.getLogger(CommandAckIngestService.class);

    private final ObjectMapper objectMapper;
    private final BusinessApiClient businessApiClient;

    public CommandAckIngestService(
            ObjectMapper objectMapper,
            BusinessApiClient businessApiClient
    ) {
        this.objectMapper = objectMapper;
        this.businessApiClient = businessApiClient;
    }

    public void accept(String topic, byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String commandNo = text(root, "commandNo");
            if (commandNo.isBlank()) {
                log.warn("设备回执缺少 commandNo，主题 {}", topic);
                return;
            }
            JsonNode successNode = root.path("success");
            boolean success = successNode.isMissingNode()
                    || successNode.asBoolean(true);

            businessApiClient.forwardCommandAck(
                    new BusinessApiClient.CommandAckMessage(
                            commandNo,
                            success,
                            text(root, "message"),
                            text(root, "executedAt")
                    )
            );
            log.info(
                    "Forwarded command ack: {} success={}",
                    commandNo,
                    success
            );
        } catch (Exception exception) {
            log.error("Failed to process command ack from {}", topic, exception);
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }
}
