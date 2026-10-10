package com.smartgreenhouse.api.modules.control;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制指令下发闭环：业务指令 → 待下发列表 → 标记已下发 → 设备回执 → 状态收敛，
 * 以及设备不回执时的超时重试与最终失败。
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class ControlCommandDispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ControlCommandService commandService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearCommands() {
        jdbcTemplate.update("DELETE FROM control_command");
    }

    /** 回执用例会把 FAN-A-02 置为 OFF，测试结束必须还原，避免污染其它用例。 */
    @AfterEach
    void restoreDeviceOperatingState() {
        jdbcTemplate.update("""
                UPDATE device
                SET operating_state = 'ON'
                WHERE device_code = 'FAN-A-02'
                """);
    }

    @Test
    void dispatchesAndAcknowledgesCommand() throws Exception {
        String commandNo = createCommand("FAN_OFF", 30);

        mockMvc.perform(
                        get("/api/control/commands/pending")
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commandNo").value(commandNo))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[0].deviceCode").value("FAN-A-02"));

        mockMvc.perform(
                        post("/api/control/commands/{commandNo}/dispatch", commandNo)
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.dispatchedAt").isNotEmpty());

        mockMvc.perform(
                        get("/api/control/commands/pending")
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(
                        get("/api/control/commands/dispatched")
                                .with(httpBasic("iot-service", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commandNo").value(commandNo))
                .andExpect(jsonPath("$.data[0].status").value("DISPATCHED"));

        mockMvc.perform(
                        post("/api/control/commands/{commandNo}/ack", commandNo)
                                .with(httpBasic("iot-service", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"success": true, "message": "设备执行完成"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        assertThat(deviceState("FAN-A-02")).isEqualTo("OFF");
    }

    @Test
    void retriesThenFailsWhenDeviceDoesNotReply() throws Exception {
        String commandNo = createCommand("FAN_ON", 30);

        expire(commandNo);
        assertThat(commandService.applyTimeouts()).isEqualTo(1);
        Map<String, Object> retried = commandRow(commandNo);
        assertThat(retried.get("status")).isEqualTo("CREATED");
        assertThat(((Number) retried.get("retry_count")).intValue()).isEqualTo(1);
        assertThat((String) retried.get("failure_reason")).contains("重试");

        expire(commandNo);
        assertThat(commandService.applyTimeouts()).isEqualTo(1);
        Map<String, Object> failed = commandRow(commandNo);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat((String) failed.get("failure_reason")).contains("仍未收到设备回执");
    }

    private String createCommand(String commandType, int timeoutSeconds)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/control/commands")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "commandType": "%s",
                                          "parameters": {"speed": 68},
                                          "operator": "基地管理员",
                                          "timeoutSeconds": %d
                                        }
                                        """.formatted(commandType, timeoutSeconds))
                )
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("commandNo")
                .asText();
    }

    private void expire(String commandNo) {
        jdbcTemplate.update("""
                UPDATE control_command
                SET attempt_started_at = ?
                WHERE command_no = ?
                """, LocalDateTime.now().minusSeconds(120), commandNo);
    }

    private Map<String, Object> commandRow(String commandNo) {
        return jdbcTemplate.queryForMap("""
                SELECT status, retry_count, failure_reason
                FROM control_command
                WHERE command_no = ?
                """, commandNo);
    }

    private String deviceState(String deviceCode) {
        return jdbcTemplate.queryForObject("""
                SELECT operating_state FROM device WHERE device_code = ?
                """, String.class, deviceCode);
    }
}
