package com.smartgreenhouse.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.business.BusinessApiClient;
import com.smartgreenhouse.iot.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制指令下发：轮询业务服务的待下发指令，发布到
 * iot/{greenhouseCode}/{deviceCode}/command，发布成功后回写 DISPATCHED。
 *
 * logging 模式下发布的只是日志网关，用于无 Broker 的演示；broker 模式下是真实 MQTT 发布。
 */
@Service
@ConditionalOnProperty(
        name = "app.command-dispatch.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CommandDispatchService {

    private static final Logger log =
            LoggerFactory.getLogger(CommandDispatchService.class);

    private final BusinessApiClient businessApiClient;
    private final MqttGateway mqttGateway;
    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public CommandDispatchService(
            BusinessApiClient businessApiClient,
            MqttGateway mqttGateway,
            MqttProperties mqttProperties,
            ObjectMapper objectMapper,
            @Value("${app.command-dispatch.batch-size:20}") int batchSize
    ) {
        this.businessApiClient = businessApiClient;
        this.mqttGateway = mqttGateway;
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.command-dispatch.interval-ms:3000}",
            initialDelayString = "${app.command-dispatch.initial-delay-ms:8000}"
    )
    public void dispatchPendingCommands() {
        List<BusinessApiClient.DeviceCommand> commands;
        try {
            commands = businessApiClient.fetchPendingCommands(batchSize);
        } catch (Exception exception) {
            log.warn("拉取待下发指令失败：{}", exception.getMessage());
            return;
        }
        for (BusinessApiClient.DeviceCommand command : commands) {
            dispatch(command);
        }
    }

    private void dispatch(BusinessApiClient.DeviceCommand command) {
        String topic = mqttProperties.getCommandTopicTemplate().formatted(
                command.greenhouseCode(),
                command.deviceCode()
        );
        try {
            byte[] payload = objectMapper.writeValueAsBytes(new CommandPayload(
                    command.commandNo(),
                    command.greenhouseCode(),
                    command.deviceCode(),
                    command.commandType(),
                    command.parameters(),
                    command.timeoutSeconds(),
                    LocalDateTime.now().toString()
            ));
            mqttGateway.publish(topic, payload);
            businessApiClient.markDispatched(command.commandNo());
            log.info(
                    "已下发指令 {} 到 {}（{}）",
                    command.commandNo(),
                    topic,
                    new String(payload, StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            // 发布失败不标记已下发，下一轮会重新取到该指令。
            log.error(
                    "下发指令 {} 失败：{}",
                    command.commandNo(),
                    exception.getMessage()
            );
        }
    }

    public record CommandPayload(
            String commandNo,
            String greenhouseCode,
            String deviceCode,
            String commandType,
            Object parameters,
            int timeoutSeconds,
            String dispatchedAt
    ) {
    }
}
