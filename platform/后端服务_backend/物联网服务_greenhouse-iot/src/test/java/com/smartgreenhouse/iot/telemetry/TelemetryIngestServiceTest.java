package com.smartgreenhouse.iot.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryIngestServiceTest {

    @Test
    void parsesAndForwardsTelemetryReadings() {
        List<TelemetryMessage> forwarded = new ArrayList<>();
        TelemetryIngestService service = new TelemetryIngestService(
                new ObjectMapper().findAndRegisterModules(),
                forwarded::add
        );

        service.accept(
                "iot/GH-001/FAN-A-02/telemetry",
                """
                {
                  "greenhouseCode": "GH-001",
                  "deviceCode": "FAN-A-02",
                  "readings": [
                    {
                      "metricCode": "temperature",
                      "value": 24.6,
                      "unit": "C",
                      "collectedAt": "2026-09-18T20:10:00"
                    },
                    {
                      "metricCode": "humidity",
                      "value": 63.2,
                      "unit": "%",
                      "collectedAt": "2026-09-18T20:10:00"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(2, forwarded.size());
        assertEquals("temperature", forwarded.get(0).metricCode());
        assertEquals("GH-001", forwarded.get(0).greenhouseCode());
        assertEquals("FAN-A-02", forwarded.get(0).deviceCode());
    }
}
