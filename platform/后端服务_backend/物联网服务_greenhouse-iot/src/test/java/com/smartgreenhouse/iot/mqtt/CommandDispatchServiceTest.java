package com.smartgreenhouse.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.business.BusinessApiClient;
import com.smartgreenhouse.iot.config.MqttProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指令下发：拉取待下发指令 → 发布到设备主题 → 回写已下发；
 * 发布失败时不得标记已下发，保证下一轮重试。
 */
class CommandDispatchServiceTest {

    private static final int BATCH_SIZE = 20;

    private final BusinessApiClient client = mock(BusinessApiClient.class);
    private final MqttGateway gateway = mock(MqttGateway.class);

    @Test
    void publishesPendingCommandAndMarksDispatched() throws Exception {
        when(client.fetchPendingCommands(BATCH_SIZE)).thenReturn(List.of(
                new BusinessApiClient.DeviceCommand(
                        "CMD-1",
                        "GH-001",
                        "FAN-A-02",
                        "FAN_ON",
                        Map.of("speed", 68),
                        30
                )
        ));

        service().dispatchPendingCommands();

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway).publish(
                eq("iot/GH-001/FAN-A-02/command"),
                payload.capture()
        );
        String json = new String(payload.getValue(), StandardCharsets.UTF_8);
        assertThat(json).contains("CMD-1").contains("FAN_ON");
        verify(client).markDispatched("CMD-1");
    }

    @Test
    void keepsCommandPendingWhenPublishFails() throws Exception {
        when(client.fetchPendingCommands(BATCH_SIZE)).thenReturn(List.of(
                new BusinessApiClient.DeviceCommand(
                        "CMD-2",
                        "GH-001",
                        "FAN-A-02",
                        "FAN_OFF",
                        Map.of(),
                        30
                )
        ));
        doThrow(new IllegalStateException("broker down"))
                .when(gateway).publish(anyString(), any());

        service().dispatchPendingCommands();

        verify(client, never()).markDispatched(anyString());
    }

    private CommandDispatchService service() {
        MqttProperties properties = new MqttProperties();
        properties.setCommandTopicTemplate("iot/%s/%s/command");
        return new CommandDispatchService(
                client,
                gateway,
                properties,
                new ObjectMapper(),
                BATCH_SIZE
        );
    }
}
