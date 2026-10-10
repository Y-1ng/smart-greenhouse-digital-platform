package com.smartgreenhouse.api.modules.external;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 外部平台接口：状态查询、手动同步留痕，以及"高危告警事务提交后自动发短信"的联动。
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class ExternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearExternalLogs() {
        jdbcTemplate.update("DELETE FROM external_sync_log");
    }

    /**
     * 告警用例会写入遥测和预警记录；测试库在同一 JVM 内共享，
     * 必须在本用例结束后清理，否则会污染后续断言环境读数的用例。
     */
    @AfterEach
    void removeTelemetryAndAlertWrittenByAlertCase() {
        jdbcTemplate.update(
                "DELETE FROM iot_telemetry WHERE raw_topic = ?",
                "iot/GH-001/FAN-A-02/telemetry"
        );
        jdbcTemplate.update(
                """
                DELETE FROM alert_record
                WHERE metric_code = 'temperature'
                  AND created_at = '2026-09-19 20:10:00'
                """
        );
    }

    @Test
    void returnsThreePlatformStatuses() throws Exception {
        mockMvc.perform(
                        get("/api/external/status")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].platform").value("JD_FARM"))
                .andExpect(jsonPath("$.data[0].mode").value("mock"))
                .andExpect(jsonPath("$.data[0].lastStatus").value("NEVER"))
                .andExpect(jsonPath("$.data[1].platform").value("WEATHER"))
                .andExpect(jsonPath("$.data[2].platform").value("SMS"));
    }

    @Test
    void manualSyncWritesLogAndUpdatesStatus() throws Exception {
        mockMvc.perform(
                        post("/api/external/sync")
                                .param("platform", "SMS")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].platform").value("SMS"))
                .andExpect(jsonPath("$.data[0].succeeded").value(1))
                .andExpect(jsonPath("$.data[0].failed").value(0));

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM external_sync_log WHERE platform = 'SMS'",
                Integer.class
        );
        assertThat(logs).isEqualTo(1);

        mockMvc.perform(
                        get("/api/external/status")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[2].lastStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data[2].successCount").value(1));
    }

    @Test
    void highTemperatureAlertTriggersSmsNotification() throws Exception {
        mockMvc.perform(
                        post("/api/iot/telemetry/readings")
                                .with(httpBasic("iot-service", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "metricCode": "temperature",
                                          "value": 38.5,
                                          "unit": "C",
                                          "collectedAt": "2026-09-19T20:10:00",
                                          "topic": "iot/GH-001/FAN-A-02/telemetry"
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        Integer alerts = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM alert_record
                WHERE metric_code = 'temperature' AND alert_level = 'HIGH'
                """, Integer.class);
        assertThat(alerts).isGreaterThanOrEqualTo(1);

        String message = jdbcTemplate.queryForObject("""
                SELECT message FROM external_sync_log
                WHERE platform = 'SMS'
                ORDER BY log_id DESC
                LIMIT 1
                """, String.class);
        assertThat(message).contains("预警短信");
    }
}
