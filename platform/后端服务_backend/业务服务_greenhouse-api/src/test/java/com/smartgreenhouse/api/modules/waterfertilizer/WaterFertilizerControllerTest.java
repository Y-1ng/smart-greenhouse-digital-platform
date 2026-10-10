package com.smartgreenhouse.api.modules.waterfertilizer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class WaterFertilizerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsCreatesAndExecutesWaterFertilizerPlan() throws Exception {
        mockMvc.perform(
                        get("/api/water-fertilizer/plans")
                                .param("greenhouseCode", "GH-001")
                                .param("date", "2026-09-18")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].planNo").value("WF-2026-0918-001"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"));

        MvcResult created = mockMvc.perform(
                        post("/api/water-fertilizer/plans")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "batchId": 2001,
                                          "recipeCode": "TOMATO-FRUIT",
                                          "recipeVersion": "V3",
                                          "ecTarget": 1.8,
                                          "phTarget": 6.2,
                                          "zoneName": "C区",
                                          "plannedAt": "2026-09-18T21:00:00",
                                          "waterVolume": 1.9,
                                          "operatorName": "农艺师"
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(
                created.getResponse().getContentAsString()
        );
        String planNo = body.path("data").path("planNo").asText();

        mockMvc.perform(
                        post("/api/water-fertilizer/plans/{planNo}/execute", planNo)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "operatorName": "基地管理员",
                                          "executedAt": "2026-09-18T21:03:00"
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planNo").value(planNo))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.executedAt").isNotEmpty());
    }
}
