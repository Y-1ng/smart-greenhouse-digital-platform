package com.smartgreenhouse.api.modules.greenhouse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class GreenhouseManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsUpdatesCropAndDeletesCustomGreenhouse() throws Exception {
        mockMvc.perform(
                        post("/api/greenhouses")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "code": "GH-099",
                                          "name": "试验温室",
                                          "area": 960,
                                          "location": "宁夏中卫",
                                          "cropName": "枸杞",
                                          "varietyName": "宁杞7号",
                                          "plantingDate": "2026-03-20",
                                          "expectedHarvestDate": "2026-08-20"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greenhouseCode").value("GH-099"))
                .andExpect(jsonPath("$.data.cropName").value("枸杞"))
                .andExpect(jsonPath("$.data.varietyName").value("宁杞7号"));

        mockMvc.perform(
                        put("/api/greenhouses/GH-099/crop")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cropName": "沙棘",
                                          "varietyName": "深秋红",
                                          "plantingDate": "2026-04-10",
                                          "expectedHarvestDate": "2026-09-10"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cropName").value("沙棘"))
                .andExpect(jsonPath("$.data.varietyName").value("深秋红"));

        mockMvc.perform(
                        get("/api/greenhouses")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.greenhouseCode == 'GH-099')].cropName")
                        .value("沙棘"));

        mockMvc.perform(
                        delete("/api/greenhouses/GH-099")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/greenhouses")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void rejectsDeletingGreenhouseWithOperationalData() throws Exception {
        mockMvc.perform(
                        delete("/api/greenhouses/GH-001")
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("该温室已有关联设备、环境周期或生产数据，不能直接删除"));
    }
}
