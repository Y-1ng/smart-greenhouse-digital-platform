package com.smartgreenhouse.api.modules.environment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class EnvironmentCycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeInsertedCycle() {
        List<Long> cycleIds = jdbcTemplate.queryForList(
                """
                SELECT cycle_id
                FROM environment_cycle
                WHERE cycle_start = '2026-09-18 18:00:00'
                  AND cycle_end = '2026-09-18 18:05:00'
                """,
                Long.class
        );
        cycleIds.forEach(cycleId -> {
            jdbcTemplate.update(
                    "DELETE FROM sensor_reading WHERE cycle_id = ?",
                    cycleId
            );
            jdbcTemplate.update(
                    "DELETE FROM environment_cycle WHERE cycle_id = ?",
                    cycleId
            );
        });
    }

    @Test
    void ingestsEnvironmentCycleAndReadings() throws Exception {
        mockMvc.perform(
                        post("/api/environment/cycles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "cycleStart": "2026-09-18T18:00:00",
                                          "cycleEnd": "2026-09-18T18:05:00",
                                          "readings": [
                                            {
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "temperature",
                                              "value": 23.40,
                                              "unit": "C",
                                              "collectedAt": "2026-09-18T18:04:00"
                                            },
                                            {
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "humidity",
                                              "value": 66.20,
                                              "unit": "%",
                                              "collectedAt": "2026-09-18T18:04:00"
                                            },
                                            {
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "light",
                                              "value": 18200.00,
                                              "unit": "lx",
                                              "collectedAt": "2026-09-18T18:04:00"
                                            },
                                            {
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "soil_moisture",
                                              "value": 44.10,
                                              "unit": "%",
                                              "collectedAt": "2026-09-18T18:04:00"
                                            }
                                          ]
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greenhouseCode").value("GH-001"))
                .andExpect(jsonPath("$.data.readingCount").value(4))
                .andExpect(jsonPath("$.data.chainStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.chainTxHash")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "0x[0-9a-f]{64}"
                        )))
                .andExpect(jsonPath("$.data.dataDigest")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "[0-9a-f]{64}"
                        )))
                .andExpect(jsonPath("$.data.rootHash")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "[0-9a-f]{64}"
                        )));

        List<String> storedHashes = jdbcTemplate.queryForList(
                """
                SELECT chain_tx_hash
                FROM environment_cycle
                WHERE cycle_start = '2026-09-18 18:00:00'
                  AND cycle_end = '2026-09-18 18:05:00'
                """,
                String.class
        );
        assertThat(storedHashes).hasSize(1);
        assertThat(storedHashes.get(0)).matches("0x[0-9a-f]{64}");
    }

    @Test
    void rejectsCycleMissingRequiredMetrics() throws Exception {
        mockMvc.perform(
                        post("/api/environment/cycles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "greenhouseCode": "GH-001",
                                          "cycleStart": "2026-09-18T20:00:00",
                                          "cycleEnd": "2026-09-18T20:05:00",
                                          "readings": [
                                            {
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "temperature",
                                              "value": 23.40,
                                              "unit": "C",
                                              "collectedAt": "2026-09-18T20:04:00"
                                            },
                                            {
                                              "deviceCode": "FAN-A-02",
                                              "metricCode": "humidity",
                                              "value": 66.20,
                                              "unit": "%",
                                              "collectedAt": "2026-09-18T20:04:00"
                                            }
                                          ]
                                        }
                                        """)
                                .with(httpBasic("admin", "123"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("光照")))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("土壤水分")));
    }
}
