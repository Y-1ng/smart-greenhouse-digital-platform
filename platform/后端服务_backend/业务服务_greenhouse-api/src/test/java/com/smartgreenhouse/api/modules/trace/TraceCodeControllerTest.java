package com.smartgreenhouse.api.modules.trace;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class TraceCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeCreatedTraceCode() {
        jdbcTemplate.update(
                "DELETE FROM trace_code WHERE product_code = ?",
                "GH-TRACE-TEST-0002"
        );
    }

    @Test
    void createsTraceCode() throws Exception {
        mockMvc.perform(
                        post("/api/trace-codes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "batchId": 2001,
                                          "fieldNo": "F-TEST-02",
                                          "productCode": "GH-TRACE-TEST-0002",
                                          "publicKeyId": "PK-GREENHOUSE-01",
                                          "signatureValue": "test-signature-value"
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode")
                        .value("GH-TRACE-TEST-0002"))
                .andExpect(jsonPath("$.data.fieldNo").value("F-TEST-02"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.chainTxHash")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "0x[0-9a-f]{64}"
                        )));
    }
}
