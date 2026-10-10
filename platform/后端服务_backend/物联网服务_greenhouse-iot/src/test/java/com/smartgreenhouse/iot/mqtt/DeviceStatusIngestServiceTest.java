package com.smartgreenhouse.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.iot.business.BusinessApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceStatusIngestServiceTest {

    @Test
    void forwardsParsedDeviceStatus() {
        BusinessApiClient client = mock(BusinessApiClient.class);
        DeviceStatusIngestService service =
                new DeviceStatusIngestService(new ObjectMapper(), client);

        service.accept(
                "iot/GH-001/FAN-A-02/status",
                """
                {"online": false, "message": "设备自检失败"}
                """.getBytes(StandardCharsets.UTF_8)
        );

        ArgumentCaptor<BusinessApiClient.DeviceStatusMessage> captor =
                ArgumentCaptor.forClass(
                        BusinessApiClient.DeviceStatusMessage.class
                );
        verify(client).forwardDeviceStatus(captor.capture());
        BusinessApiClient.DeviceStatusMessage message = captor.getValue();
        assertThat(message.greenhouseCode()).isEqualTo("GH-001");
        assertThat(message.deviceCode()).isEqualTo("FAN-A-02");
        assertThat(message.online()).isFalse();
        assertThat(message.message()).isEqualTo("设备自检失败");
    }
}
