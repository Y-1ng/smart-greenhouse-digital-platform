package com.smartgreenhouse.iot.mqtt;

import com.smartgreenhouse.iot.telemetry.TelemetryIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MQTT 主题路由：按主题后缀区分遥测、设备状态和指令回执，
 * 避免所有上行消息都被当成遥测解析。
 */
@Service
public class MqttMessageRouter {

    private static final Logger log =
            LoggerFactory.getLogger(MqttMessageRouter.class);

    private final TelemetryIngestService telemetryIngestService;
    private final DeviceStatusIngestService deviceStatusIngestService;
    private final CommandAckIngestService commandAckIngestService;

    public MqttMessageRouter(
            TelemetryIngestService telemetryIngestService,
            DeviceStatusIngestService deviceStatusIngestService,
            CommandAckIngestService commandAckIngestService
    ) {
        this.telemetryIngestService = telemetryIngestService;
        this.deviceStatusIngestService = deviceStatusIngestService;
        this.commandAckIngestService = commandAckIngestService;
    }

    public void route(String topic, byte[] payload) {
        if (topic.endsWith("/status")) {
            deviceStatusIngestService.accept(topic, payload);
            return;
        }
        if (topic.endsWith("/ack")) {
            commandAckIngestService.accept(topic, payload);
            return;
        }
        if (topic.endsWith("/telemetry")) {
            telemetryIngestService.accept(topic, payload);
            return;
        }
        log.warn("忽略未知 MQTT 主题：{}", topic);
    }
}
