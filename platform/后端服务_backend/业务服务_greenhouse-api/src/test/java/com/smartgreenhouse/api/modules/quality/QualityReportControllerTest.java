package com.smartgreenhouse.api.modules.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class QualityReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTestReport() {
        jdbcTemplate.update(
                "DELETE FROM quality_report WHERE report_no = ?",
                "QR-TEST-0001"
        );
    }

    @Test
    void uploadsAndQueriesQualityReport() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                "quality report content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/quality/reports")
                                .file(file)
                                .param("batchId", "2001")
                                .param("institutionId", "ORG-001")
                                .param("institutionName", "寒旱区检测中心")
                                .param("reportNo", "QR-TEST-0001")
                                .param("resultSummary", "检测合格")
                                .param("testedAt", "2026-09-18T10:00:00")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportNo")
                        .value("QR-TEST-0001"))
                .andExpect(jsonPath("$.data.contentHash").isNotEmpty())
                .andExpect(jsonPath("$.data.fileUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("STORED"));

        mockMvc.perform(
                        get("/api/quality/reports/QR-TEST-0001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportNo")
                        .value("QR-TEST-0001"))
                .andExpect(jsonPath("$.data.resultSummary").value("检测合格"))
                .andExpect(jsonPath("$.data.status").value("STORED"));

        mockMvc.perform(
                        get("/api/quality/reports/QR-TEST-0001/file")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(content().bytes(
                        "quality report content".getBytes(StandardCharsets.UTF_8)
                ));
    }
}
