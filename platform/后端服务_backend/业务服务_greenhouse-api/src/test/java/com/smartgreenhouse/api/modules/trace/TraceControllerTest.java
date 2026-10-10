package com.smartgreenhouse.api.modules.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class TraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearScanRecords() {
        jdbcTemplate.update("DELETE FROM scan_record");
    }

    @Test
    void returnsVerifiedTracePath() throws Exception {
        mockMvc.perform(get("/api/trace/GH-TRACE-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.stages.length()").value(5))
                .andExpect(jsonPath("$.data.stages[0].stageType").value("种植"))
                .andExpect(jsonPath("$.data.certificateNo")
                        .value("CERT-2026-0916-0031"))
                .andExpect(jsonPath("$.data.environment.cycleId").value(1001))
                .andExpect(jsonPath("$.data.environment.chainStatus")
                        .value("CONFIRMED"))
                .andExpect(jsonPath("$.data.environment.metrics.length()")
                        .value(4))
                .andExpect(jsonPath("$.data.environment.metrics[0].metricCode")
                        .value("temperature"))
                .andExpect(jsonPath("$.data.environment.metrics[0].value")
                        .value(22.6))
                .andExpect(jsonPath("$.data.environment.metrics[1].metricCode")
                        .value("humidity"))
                .andExpect(jsonPath("$.data.environment.metrics[2].metricCode")
                        .value("light"))
                .andExpect(jsonPath("$.data.environment.metrics[3].metricCode")
                        .value("soil_moisture"))
                .andExpect(jsonPath("$.data.environment.metrics[3].value")
                        .value(43.2));
    }

    @Test
    void returnsCompleteFieldTracePath() throws Exception {
        mockMvc.perform(get("/api/trace/field/F-A-03/path"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fieldNo").value("F-A-03"))
                .andExpect(jsonPath("$.data.productCode")
                        .value("GH-TRACE-2026-0001"))
                .andExpect(jsonPath("$.data.pointerChainValid").value(true))
                .andExpect(jsonPath("$.data.stages.length()").value(5));
    }

    @Test
    void recordsFirstAndRepeatedScans() throws Exception {
        mockMvc.perform(
                        post("/api/trace/codes/GH-TRACE-2026-0001/scan")
                                .with(httpBasic("admin", "123"))
                                .contentType("application/json")
                                .content("""
                                        {"channel": "MOBILE", "deviceFingerprint": "demo-device"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstScan").value(true))
                .andExpect(jsonPath("$.data.scanCount").value(1))
                .andExpect(jsonPath("$.data.result").value("FIRST_SCAN"))
                .andExpect(jsonPath("$.data.chainTxHash")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "0x[0-9a-f]{64}"
                        )));

        mockMvc.perform(
                        post("/api/trace/codes/GH-TRACE-2026-0001/scan")
                                .with(httpBasic("admin", "123"))
                                .contentType("application/json")
                                .content("""
                                        {"channel": "MOBILE", "deviceFingerprint": "demo-device"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstScan").value(false))
                .andExpect(jsonPath("$.data.scanCount").value(2))
                .andExpect(jsonPath("$.data.result").value("REPEAT_SCAN"))
                .andExpect(jsonPath("$.data.chainTxHash")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "0x[0-9a-f]{64}"
                        )));
    }
}
