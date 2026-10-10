package com.smartgreenhouse.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.business.BusinessApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommandAckIngestServiceTest {

    @Test
    void forwardsParsedCommandAck() {
        BusinessApiClient client = mock(BusinessApiClient.class);
        CommandAckIngestService service =
                new CommandAckIngestService(new ObjectMapper(), client);

        service.accept(
                "iot/GH-001/FAN-A-02/ack",
                """
                {
                  "commandNo": "CMD-1",
                  "success": true,
                  "message": "执行完成",
                  "executedAt": "2026-09-19T18:00:00"
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        ArgumentCaptor<BusinessApiClient.CommandAckMessage> captor =
                ArgumentCaptor.forClass(
                        BusinessApiClient.CommandAckMessage.class
                );
        verify(client).forwardCommandAck(captor.capture());
        BusinessApiClient.CommandAckMessage message = captor.getValue();
        assertThat(message.commandNo()).isEqualTo("CMD-1");
        assertThat(message.success()).isTrue();
        assertThat(message.executedAt()).isEqualTo("2026-09-19T18:00:00");
    }
}
