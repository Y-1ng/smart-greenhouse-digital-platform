package com.smartgreenhouse.api.modules.cooperation;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class CooperationWriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeCreatedRecords() {
        jdbcTemplate.update(
                """
                DELETE FROM dividend_record
                WHERE share_id IN (
                  SELECT share_id
                  FROM cooperation_share
                  WHERE project_name = '测试合作项目'
                )
                """
        );
        jdbcTemplate.update(
                "DELETE FROM cooperation_share WHERE project_name = ?",
                "测试合作项目"
        );
        jdbcTemplate.update(
                "DELETE FROM village WHERE village_name = ?",
                "测试自定义村"
        );
    }

    @Test
    void createsShareAndDividend() throws Exception {
        MvcResult shareResult = mockMvc.perform(
                        post("/api/cooperation/shares")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "villageId": 7001,
                                          "projectName": "测试合作项目",
                                          "shareRatio": 0.15,
                                          "effectiveDate": "2026-10-01",
                                          "status": "ACTIVE"
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareRatio").value(0.15))
                .andReturn();
        JsonNode shareBody = objectMapper.readTree(
                shareResult.getResponse().getContentAsString()
        );
        long shareId = shareBody.path("data").path("shareId").asLong();

        mockMvc.perform(
                        post("/api/cooperation/dividends")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "shareId": %d,
                                          "periodName": "2026 Q4",
                                          "amount": 56000.00,
                                          "status": "PENDING"
                                        }
                                        """.formatted(shareId))
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareId").value(shareId))
                .andExpect(jsonPath("$.data.amount").value(56000.0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(
                        delete("/api/cooperation/shares/{shareId}", shareId)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(
                        get("/api/cooperation/villages")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.shareId == %d)]".formatted(shareId))
                        .isEmpty());
    }

    @Test
    void createsShareFromCustomVillageName() throws Exception {
        MvcResult shareResult = mockMvc.perform(
                        post("/api/cooperation/shares")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "villageName": "测试自定义村",
                                          "projectName": "测试合作项目",
                                          "shareRatio": 0.12,
                                          "effectiveDate": "2026-10-01",
                                          "status": "ACTIVE"
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareRatio").value(0.12))
                .andReturn();
        long shareId = objectMapper.readTree(
                shareResult.getResponse().getContentAsString()
        ).path("data").path("shareId").asLong();

        mockMvc.perform(
                        delete("/api/cooperation/shares/{shareId}", shareId)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}
