package com.smartgreenhouse.api.modules.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备状态上报：MQTT 状态主题转发到业务服务后，设备在线状态与最后在线时间必须落库。
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class IotDeviceStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updatesDeviceOnlineStatusFromReport() throws Exception {
        mockMvc.perform(
                        post("/api/iot/devices/status")
                                .with(httpBasic("iot-service", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "online": false,
                                          "message": "设备自检失败"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.data.lastSeenAt").isNotEmpty());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT online_status, last_seen_at
                FROM device
                WHERE device_code = 'FAN-A-02'
                """);
        assertThat(row.get("online_status")).isEqualTo("OFFLINE");
        assertThat(row.get("last_seen_at")).isNotNull();
    }

    @Test
    void telemetryArrivalMarksDeviceOnline() throws Exception {
        jdbcTemplate.update("""
                UPDATE device SET online_status = 'OFFLINE'
                WHERE device_code = 'VALVE-B-01'
                """);

        mockMvc.perform(
                        post("/api/iot/telemetry/readings")
                                .with(httpBasic("iot-service", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "VALVE-B-01",
                                          "metricCode": "soil_moisture",
                                          "value": 42.5,
                                          "unit": "%",
                                          "collectedAt": "2026-09-19T18:00:00",
                                          "topic": "iot/GH-001/VALVE-B-01/telemetry"
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject("""
                SELECT online_status FROM device WHERE device_code = 'VALVE-B-01'
                """, String.class);
        assertThat(status).isEqualTo("ONLINE");
    }
}
