package com.smartgreenhouse.iot.mqtt;

import com.smartgreenhouse.iot.telemetry.TelemetryIngestService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MQTT 主题路由：按主题后缀分发到遥测、设备状态、指令回执三条处理链。
 */
class MqttMessageRouterTest {

    private final TelemetryIngestService telemetry =
            mock(TelemetryIngestService.class);
    private final DeviceStatusIngestService deviceStatus =
            mock(DeviceStatusIngestService.class);
    private final CommandAckIngestService commandAck =
            mock(CommandAckIngestService.class);

    private final MqttMessageRouter router =
            new MqttMessageRouter(telemetry, deviceStatus, commandAck);

    @Test
    void routesTelemetryTopic() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String topic = "iot/GH-001/FAN-A-02/telemetry";

        router.route(topic, payload);

        verify(telemetry).accept(topic, payload);
        verify(deviceStatus, never()).accept(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void routesDeviceStatusTopic() {
        byte[] payload = "{\"online\":false}".getBytes(StandardCharsets.UTF_8);
        String topic = "iot/GH-001/FAN-A-02/status";

        router.route(topic, payload);

        verify(deviceStatus).accept(topic, payload);
        verify(telemetry, never()).accept(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void routesCommandAckTopic() {
        byte[] payload = "{\"commandNo\":\"CMD-1\"}"
                .getBytes(StandardCharsets.UTF_8);
        String topic = "iot/GH-001/FAN-A-02/ack";

        router.route(topic, payload);

        verify(commandAck).accept(topic, payload);
        verify(telemetry, never()).accept(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
