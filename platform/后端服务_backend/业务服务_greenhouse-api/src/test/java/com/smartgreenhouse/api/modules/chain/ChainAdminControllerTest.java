package com.smartgreenhouse.api.modules.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class ChainAdminControllerTest {

    private static final String TX_PATTERN = "0x[0-9a-f]{64}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void retriesPendingEnvironmentCycleAndWritesReceipt() throws Exception {
        jdbcTemplate.update("""
                UPDATE environment_cycle
                SET chain_status = 'PENDING', chain_tx_hash = NULL
                WHERE cycle_id = 1001
                """);

        mockMvc.perform(
                        get("/api/chain/status")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adapter.mode")
                        .value("simulated"))
                .andExpect(jsonPath("$.data.adapter.reachable").value(false))
                .andExpect(jsonPath("$.data.pending.environmentCycle").value(1))
                .andExpect(jsonPath("$.data.pending.total")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(
                        post("/api/chain/compensate")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempted")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.confirmed")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.entries[0].type")
                        .value("ENVIRONMENT_CYCLE"))
                .andExpect(jsonPath("$.data.entries[0].businessId")
                        .value("1001"))
                .andExpect(jsonPath("$.data.entries[0].transactionHash")
                        .value(matchesPattern(TX_PATTERN)));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT chain_status, chain_tx_hash
                FROM environment_cycle
                WHERE cycle_id = 1001
                """);
        assertThat(row.get("chain_status")).isEqualTo("CONFIRMED");
        assertThat((String) row.get("chain_tx_hash")).matches(TX_PATTERN);

        mockMvc.perform(
                        get("/api/chain/status")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending.total").value(0));
    }

    @Test
    void allowsQualityRoleToReadStatus() throws Exception {
        mockMvc.perform(
                        get("/api/chain/status")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + qualityToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adapter.mode").value("simulated"));
    }

    @Test
    void rejectsCompensationFromQualityRole() throws Exception {
        mockMvc.perform(
                        post("/api/chain/compensate")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + qualityToken()
                                )
                )
                .andExpect(status().isForbidden());
    }

    private String qualityToken() throws Exception {
        MvcResult login = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "quality",
                                          "password": "123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                login.getResponse().getContentAsString()
        ).path("data").path("accessToken").asText();
    }
}
