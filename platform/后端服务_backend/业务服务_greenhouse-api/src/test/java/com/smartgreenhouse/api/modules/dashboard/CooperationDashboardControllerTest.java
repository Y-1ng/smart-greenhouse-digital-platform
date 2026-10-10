package com.smartgreenhouse.api.modules.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class CooperationDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsVillageCooperationOverview() throws Exception {
        mockMvc.perform(
                        get("/api/cooperation/villages")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].villageName").value("示范村一"))
                .andExpect(jsonPath("$.data[0].landArea").value(320.0))
                .andExpect(jsonPath("$.data[0].shareRatio").value(0.38))
                .andExpect(jsonPath("$.data[0].dividendAmount").value(128000.0))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));
    }

    @Test
    void returnsGovernmentDashboardMetrics() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/dashboard/government")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greenhouseCount").value(1))
                .andExpect(jsonPath("$.data.deviceOnlineRate").value(1.0))
                .andExpect(jsonPath("$.data.traceabilityCoverage").value(1.0))
                .andExpect(jsonPath("$.data.offlineDeviceCount").value(0))
                .andExpect(jsonPath("$.data.pendingChainProofCount").value(0))
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty())
                .andReturn();

        // 扫码风控记录由其他用例写入，这里只校验风险总数与三项明细一致。
        JsonNode data = new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data");
        assertThat(data.path("repeatScanCount").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(data.path("activeAlerts").asInt()).isEqualTo(
                data.path("offlineDeviceCount").asInt()
                        + data.path("pendingChainProofCount").asInt()
                        + data.path("repeatScanCount").asInt()
        );
    }
}
