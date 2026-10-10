package com.smartgreenhouse.api.modules.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 追加田块追溯节点：节点落库、按指针顺序上链、链上回执写回。
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class TraceNodeAppendControllerTest {

    private static final String SEEDED_TAIL_HASH = "5" + "0".repeat(63);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appendsTraceNodeAndWritesChainReceipt() throws Exception {
        try {
            mockMvc.perform(
                            post("/api/trace/fields/F-A-03/nodes")
                                    .with(httpBasic("admin", "123"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "stageType": "运输",
                                              "summary": "发往冷链中转仓"
                                            }
                                            """)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fieldNo").value("F-A-03"))
                    .andExpect(jsonPath("$.data.stageType").value("运输"))
                    .andExpect(jsonPath("$.data.previousHash")
                            .value(SEEDED_TAIL_HASH))
                    .andExpect(jsonPath("$.data.payloadHash")
                            .value(matchesPattern("[0-9a-f]{64}")))
                    .andExpect(jsonPath("$.data.chainTxHash")
                            .value(matchesPattern("0x[0-9a-f]{64}")))
                    .andExpect(jsonPath("$.data.pointerChainValid").value(true))
                    .andExpect(jsonPath("$.data.nodeCount")
                            .value(greaterThanOrEqualTo(6)));

            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT previous_hash, chain_tx_hash
                    FROM trace_node
                    WHERE stage_type = '运输'
                    ORDER BY node_id DESC
                    LIMIT 1
                    """);
            assertThat(row.get("previous_hash")).isEqualTo(SEEDED_TAIL_HASH);
            assertThat((String) row.get("chain_tx_hash"))
                    .matches("0x[0-9a-f]{64}");
        } finally {
            // 只清理本用例新增的节点（演示数据最大 node_id 为 6005）
            jdbcTemplate.update("DELETE FROM trace_node WHERE node_id > 6005");
        }
    }

    @Test
    void rejectsUnknownField() throws Exception {
        mockMvc.perform(
                        post("/api/trace/fields/F-NONE/nodes")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"stageType": "运输"}
                                        """)
                )
                .andExpect(status().isNotFound());
    }
}
