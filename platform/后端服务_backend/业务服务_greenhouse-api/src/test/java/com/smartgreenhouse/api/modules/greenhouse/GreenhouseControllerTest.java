package com.smartgreenhouse.api.modules.greenhouse;

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
class GreenhouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsGreenhouseOverviewByCode() throws Exception {
        mockMvc.perform(
                        get("/api/greenhouses/GH-001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data.greenhouseName").value("1号温室"))
                .andExpect(jsonPath("$.data.metrics.temperature").value(22.6))
                .andExpect(jsonPath("$.data.devices[0].deviceCode").value("FAN-A-02"))
                .andExpect(jsonPath("$.data.devices[0].operatingState").value("ON"))
                .andExpect(jsonPath("$.data.devices[1].operatingState").value("OFF"));
    }

    @Test
    void returnsAvailableGreenhouses() throws Exception {
        mockMvc.perform(
                        get("/api/greenhouses")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data[0].greenhouseName").value("1号温室"))
                .andExpect(jsonPath("$.data[0].cropName").value("番茄"))
                .andExpect(jsonPath("$.data[0].varietyName").value("金棚一号"))
                .andExpect(jsonPath("$.data[0].batchId").value(2001));
    }
}
