package com.smartgreenhouse.api.modules.environment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class EnvironmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsLatestDatabaseSnapshot() throws Exception {
        mockMvc.perform(
                        get("/api/environment/latest")
                                .param("greenhouseId", "GH-001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greenhouseId").value("GH-001"))
                .andExpect(jsonPath("$.data.metrics.temperature").value(22.6))
                .andExpect(jsonPath("$.data.metrics.soil_moisture").value(43.2))
                .andExpect(jsonPath("$.data.source").value("DATABASE"))
                .andExpect(jsonPath("$.data.rootHash")
                        .value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    void returnsRecentEnvironmentTrend() throws Exception {
        mockMvc.perform(
                        get("/api/environment/trend")
                                .param("greenhouseCode", "GH-001")
                                .param("limit", "6")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.points[0].cycleId").value(1001))
                .andExpect(jsonPath("$.data.points[0].metrics.humidity").value(68.4));
    }
}
