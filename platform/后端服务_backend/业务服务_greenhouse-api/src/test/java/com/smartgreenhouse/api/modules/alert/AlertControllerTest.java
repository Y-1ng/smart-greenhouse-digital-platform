package com.smartgreenhouse.api.modules.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM alert_record WHERE metric_code = 'temperature'"
        );
        jdbcTemplate.update(
                "DELETE FROM iot_telemetry WHERE raw_topic = ?",
                "iot/GH-001/FAN-A-02/telemetry"
        );
    }

    @Test
    void recordsAlertForOutOfRangeTelemetryAndAllowsAcknowledge()
            throws Exception {
        ingest(33.50, "2026-09-19T08:00:00");

        MvcResult result = mockMvc.perform(
                        get("/api/alerts")
                                .param("greenhouseCode", "GH-001")
                                .param("status", "PENDING")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].greenhouseCode")
                        .value("GH-001"))
                .andExpect(jsonPath("$.data[0].metricCode")
                        .value("temperature"))
                .andExpect(jsonPath("$.data[0].alertLevel").value("HIGH"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].message")
                        .value("空气温度 33.5 高于告警上限 32"))
                .andReturn();

        long alertId = new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .get(0)
                .path("alertId")
                .asLong();

        mockMvc.perform(
                        post("/api/alerts/" + alertId + "/ack")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operator\":\"基地管理员\"}")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.acknowledgedBy")
                        .value("基地管理员"));
    }

    @Test
    void keepsSinglePendingAlertForRepeatedReadings() throws Exception {
        ingest(33.50, "2026-09-19T08:00:00");
        ingest(34.20, "2026-09-19T08:05:00");

        mockMvc.perform(
                        get("/api/alerts")
                                .param("greenhouseCode", "GH-001")
                                .param("status", "PENDING")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private void ingest(double value, String collectedAt) throws Exception {
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
                                        """.formatted(value, collectedAt))
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk());
    }
}
