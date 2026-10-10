package com.smartgreenhouse.api.modules.telemetry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoTelemetryPublisherTest {

    @Test
    void publishesFourMetricsThroughIngestService() {
        IotTelemetryMapper mapper = mock(IotTelemetryMapper.class);
        IotTelemetryService telemetryService = mock(IotTelemetryService.class);
        when(mapper.findGreenhouseByCode("GH-001"))
                .thenReturn(new IotTelemetryMapper.GreenhouseRow(1L, "GH-001"));
        when(mapper.findFirstDeviceCode(1L)).thenReturn("FAN-A-02");
        when(mapper.findDeviceId(1L, "FAN-A-02")).thenReturn(1L);

        DemoTelemetryPublisher publisher =
                new DemoTelemetryPublisher(
                        mapper,
                        telemetryService,
                        "GH-001",
                        false
                );
        publisher.publish();

        ArgumentCaptor<IotTelemetryController.IngestRequest> captor =
                ArgumentCaptor.forClass(
                        IotTelemetryController.IngestRequest.class
                );
        verify(telemetryService, times(4)).ingest(captor.capture());

        List<IotTelemetryController.IngestRequest> requests =
                captor.getAllValues();
        assertThat(requests)
                .extracting(IotTelemetryController.IngestRequest::metricCode)
                .containsExactly(
                        "temperature",
                        "humidity",
                        "light",
                        "soil_moisture"
                );
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.greenhouseCode()).isEqualTo("GH-001");
            assertThat(request.deviceCode()).isEqualTo("FAN-A-02");
            assertThat(request.collectedAt()).isNotNull();
            assertThat(request.topic())
                    .isEqualTo("iot/GH-001/FAN-A-02/telemetry");
        });
        assertThat(requests.get(0).value())
                .isBetween(new BigDecimal("19.0"), new BigDecimal("26.0"));
        assertThat(requests.get(1).value())
                .isBetween(new BigDecimal("63.0"), new BigDecimal("73.0"));
        assertThat(requests.get(2).value())
                .isBetween(new BigDecimal("15000"), new BigDecimal("22000"));
        assertThat(requests.get(3).value())
                .isBetween(new BigDecimal("39.0"), new BigDecimal("47.0"));
    }

    @Test
    void skipsWhenGreenhouseIsMissing() {
        IotTelemetryMapper mapper = mock(IotTelemetryMapper.class);
        IotTelemetryService telemetryService = mock(IotTelemetryService.class);
        when(mapper.findGreenhouseByCode("GH-404")).thenReturn(null);

        DemoTelemetryPublisher publisher =
                new DemoTelemetryPublisher(
                        mapper,
                        telemetryService,
                        "GH-404",
                        false
                );
        publisher.publish();

        verify(telemetryService, never()).ingest(
                ArgumentMatchers.any(
                        IotTelemetryController.IngestRequest.class
                )
        );
    }
}
