package com.smartgreenhouse.api.modules.control;

import com.jayway.jsonpath.JsonPath;
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
class ControlCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreDeviceState() {
        jdbcTemplate.update(
                """
                UPDATE device
                SET operating_state = 'ON'
                WHERE device_code = 'FAN-A-02'
                """
        );
    }

    @Test
    void createsAndQueriesControlCommand() throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/control/commands")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "commandType": "FAN_ON",
                                          "parameters": {"speed": 68},
                                          "operator": "基地管理员",
                                          "timeoutSeconds": 30
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.commandNo").isNotEmpty())
                .andReturn();

        String commandNo = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.commandNo"
        );

        mockMvc.perform(
                        get("/api/control/commands/{commandNo}", commandNo)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commandNo").value(commandNo))
                .andExpect(jsonPath("$.data.deviceCode").value("FAN-A-02"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void recordsSuccessfulDeviceAcknowledgement() throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/control/commands")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "commandType": "FAN_ON",
                                          "parameters": {"speed": 68},
                                          "operator": "基地管理员",
                                          "timeoutSeconds": 30
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();

        String commandNo = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.commandNo"
        );

        mockMvc.perform(
                        post(
                                "/api/control/commands/{commandNo}/ack",
                                commandNo
                        )
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "success": true,
                                          "message": "设备已执行",
                                          "executedAt": "2026-09-18T14:40:00"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        mockMvc.perform(
                        get("/api/control/commands/{commandNo}", commandNo)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }

    @Test
    void updatesDeviceOperatingStateAfterSuccessfulOffCommand() throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/control/commands")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "deviceCode": "FAN-A-02",
                                          "commandType": "FAN_OFF",
                                          "parameters": {},
                                          "operator": "基地管理员",
                                          "timeoutSeconds": 30
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();

        String commandNo = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.commandNo"
        );
        mockMvc.perform(
                        post(
                                "/api/control/commands/{commandNo}/ack",
                                commandNo
                        )
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "success": true,
                                          "message": "设备已关闭",
                                          "executedAt": "2026-09-18T14:42:00"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        String operatingState = jdbcTemplate.queryForObject(
                """
                SELECT operating_state
                FROM device
                WHERE device_code = 'FAN-A-02'
                """,
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(operatingState)
                .isEqualTo("OFF");
    }
}
