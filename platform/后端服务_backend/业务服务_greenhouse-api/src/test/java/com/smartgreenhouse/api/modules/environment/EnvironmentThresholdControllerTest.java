package com.smartgreenhouse.api.modules.environment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class EnvironmentThresholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreThresholds() {
        jdbcTemplate.update(
                """
                UPDATE environment_threshold
                SET
                  target_min = 18.0,
                  target_max = 28.0,
                  alert_min = 12.0,
                  alert_max = 32.0,
                  control_action = 'VENTILATION',
                  enabled = 1
                WHERE metric_code = 'temperature'
                """
        );
    }

    @Test
    void listsAndUpdatesThresholds() throws Exception {
        mockMvc.perform(
                        get("/api/environment/thresholds")
                                .param("greenhouseCode", "GH-001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].metricCode")
                        .value("temperature"))
                .andExpect(jsonPath("$.data[0].targetMin").value(18.0))
                .andExpect(jsonPath("$.data[0].targetMax").value(28.0));

        mockMvc.perform(
                        put("/api/environment/thresholds/temperature")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "targetMin": 19.0,
                                          "targetMax": 29.0,
                                          "alertMin": 13.0,
                                          "alertMax": 33.0,
                                          "controlAction": "VENTILATION",
                                          "enabled": false
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricCode").value("temperature"))
                .andExpect(jsonPath("$.data.targetMin").value(19.0))
                .andExpect(jsonPath("$.data.targetMax").value(29.0))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }
}
