package com.smartgreenhouse.api.modules.quality;

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
class CertificateManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeCreatedCertificate() {
        jdbcTemplate.update(
                "DELETE FROM certificate WHERE certificate_no = ?",
                "CERT-TEST-2026-0002"
        );
    }

    @Test
    void createsCertificateRecord() throws Exception {
        mockMvc.perform(
                        post("/api/certificates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "batchId": 2001,
                                          "certificateNo": "CERT-TEST-2026-0002",
                                          "contentHash": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                                          "status": "VALID"
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(2001))
                .andExpect(jsonPath("$.data.certificateNo")
                        .value("CERT-TEST-2026-0002"))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.chainTxHash")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "0x[0-9a-f]{64}"
                        )))
                .andExpect(jsonPath("$.data.issuedAt").isNotEmpty());
    }
}
