package com.smartgreenhouse.api.modules.proof;

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
class ProofControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsBatchProofSummary() throws Exception {
        mockMvc.perform(
                        get("/api/proofs/2001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(2001))
                .andExpect(jsonPath("$.data.greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data.rootHash")
                        .value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(jsonPath("$.data.chainStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.certificateNo")
                        .value("CERT-2026-0916-0031"))
                .andExpect(jsonPath("$.data.productCode")
                        .value("GH-TRACE-2026-0001"))
                .andExpect(jsonPath("$.data.verificationStatus")
                        .value("VERIFIED"));
    }
}
