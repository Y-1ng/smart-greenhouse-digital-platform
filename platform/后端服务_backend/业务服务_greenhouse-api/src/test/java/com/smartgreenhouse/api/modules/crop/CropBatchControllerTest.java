package com.smartgreenhouse.api.modules.crop;

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
class CropBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsCropBatchesForReportUpload() throws Exception {
        mockMvc.perform(
                        get("/api/crop-batches")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].batchId").value(2001))
                .andExpect(jsonPath("$.data[0].greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data[0].cropName").value("番茄"))
                .andExpect(jsonPath("$.data[0].varietyName").value("金棚一号"));
    }
}
