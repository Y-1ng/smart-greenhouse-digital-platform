package com.smartgreenhouse.api.modules.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class IotTelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeInsertedTelemetry() {
        jdbcTemplate.update(
                "DELETE FROM iot_telemetry WHERE raw_topic = ?",
                "iot/GH-001/FAN-A-02/telemetry"
        );
    }

    @Test
    void ingestsAndQueriesIotTelemetry() throws Exception {
        mockMvc.perform(
                        post("/api/iot/telemetry/readings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "metricCode": "temperature",
                                          "value": 24.80,
                                          "unit": "C",
                                          "collectedAt": "2026-09-18T20:00:00",
                                          "topic": "iot/GH-001/FAN-A-02/telemetry"
                                        }
                                        """)
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data.deviceCode").value("FAN-A-02"))
                .andExpect(jsonPath("$.data.value").value(24.8))
                .andExpect(jsonPath("$.data.topic")
                        .value("iot/GH-001/FAN-A-02/telemetry"));

        mockMvc.perform(
                        get("/api/iot/telemetry/latest")
                                .param("greenhouseCode", "GH-001")
                                .param("metricCode", "temperature")
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].value").value(24.8))
                .andExpect(jsonPath("$.data[0].collectedAt")
                        .value("2026-09-18T20:00:00"));
    }

    @Test
    void greenhouseOverviewPrefersLatestTelemetry() throws Exception {
        mockMvc.perform(
                        post("/api/iot/telemetry/readings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "metricCode": "temperature",
                                          "value": 31.40,
                                          "unit": "C",
                                          "collectedAt": "2026-09-18T20:20:00",
                                          "topic": "iot/GH-001/FAN-A-02/telemetry"
                                        }
                                        """)
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/greenhouses/GH-001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.temperature")
                        .value(31.4))
                .andExpect(jsonPath("$.data.metrics.humidity")
                        .value(68.4));
    }

    @Test
    void returnsBucketedTelemetryHistory() throws Exception {
        LocalDateTime base = LocalDateTime.now().withNano(0).minusMinutes(1);
        for (int index = 0; index < 3; index += 1) {
            mockMvc.perform(
                            post("/api/iot/telemetry/readings")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "greenhouseCode": "GH-001",
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "temperature",
                                              "value": %s,
                                              "unit": "C",
                                              "collectedAt": "%s",
                                              "topic": "iot/GH-001/FAN-A-02/telemetry"
                                            }
                                            """.formatted(
                                            20.0 + index,
                                            base.plusSeconds(index * 20L)
                                    ))
                                    .with(httpBasic("iot-service", "123"))
                    )
                    .andExpect(status().isOk());
        }

        mockMvc.perform(
                        get("/api/iot/telemetry/history")
                                .param("greenhouseCode", "GH-001")
                                .param("minutes", "5")
                                .param("bucketSeconds", "20")
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minutes").value(5))
                .andExpect(jsonPath("$.data.bucketSeconds").value(20))
                .andExpect(jsonPath("$.data.samples.length()").value(3))
                .andExpect(jsonPath("$.data.samples[0].metrics.temperature")
                        .value(20.0));
    }
}
