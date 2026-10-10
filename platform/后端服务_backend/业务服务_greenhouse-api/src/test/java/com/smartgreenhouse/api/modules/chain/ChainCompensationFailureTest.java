package com.smartgreenhouse.api.modules.chain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 适配服务不可达时，补偿必须保留 PENDING 且不写交易哈希。
 * 端点指向本机空闲端口，模拟链上服务宕机。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.chain.mode=rest",
        "app.chain.endpoint=http://127.0.0.1:19998",
        "app.chain.timeout-ms=500",
        "app.chain.compensation.enabled=false"
})
class ChainCompensationFailureTest {

    private static final String CYCLE_START = "2026-09-19 08:00:00";
    private static final String CYCLE_END = "2026-09-19 08:05:00";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChainCompensationService compensationService;

    @Test
    void keepsRecordPendingWhenAdapterUnreachable() {
        jdbcTemplate.update("""
                INSERT INTO environment_cycle (
                  greenhouse_id, cycle_start, cycle_end,
                  data_digest, root_hash, chain_status
                )
                VALUES (1, ?, ?, ?, ?, 'PENDING')
                """, CYCLE_START, CYCLE_END, "d".repeat(64), "e".repeat(64));
        try {
            ChainCompensationReport report = compensationService.compensate();

            assertThat(report.attempted()).isGreaterThanOrEqualTo(1);
            assertThat(report.confirmed()).isZero();
            assertThat(report.failed()).isGreaterThanOrEqualTo(1);
            assertThat(report.failures()).isNotEmpty();

            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT chain_status, chain_tx_hash
                    FROM environment_cycle
                    WHERE cycle_start = ? AND cycle_end = ?
                    """, CYCLE_START, CYCLE_END);
            assertThat(row.get("chain_status")).isEqualTo("PENDING");
            assertThat(row.get("chain_tx_hash")).isNull();
        } finally {
            jdbcTemplate.update("""
                    DELETE FROM environment_cycle
                    WHERE cycle_start = ? AND cycle_end = ?
                    """, CYCLE_START, CYCLE_END);
        }
    }

    /**
     * 溯源码尚未上链时，不应反复提交依赖它的扫码记录：
     * 否则每轮补偿都会被合约以「trace code not found」拒绝，日志噪声不断。
     */
    @Test
    void doesNotRetryScanRecordBeforeTraceCodeIsOnChain() {
        jdbcTemplate.update("""
                UPDATE trace_code SET chain_tx_hash = NULL
                WHERE product_code = 'GH-TRACE-2026-0001'
                """);
        jdbcTemplate.update("""
                INSERT INTO scan_record (
                  trace_code_id, scan_time, scan_channel, ip_hash,
                  risk_result, chain_tx_hash
                )
                SELECT trace_code_id, CURRENT_TIMESTAMP, 'WECHAT_MINI', ?,
                       'FIRST_SCAN', NULL
                FROM trace_code
                WHERE product_code = 'GH-TRACE-2026-0001'
                """, "f".repeat(64));
        try {
            ChainCompensationReport report = compensationService.compensate();

            assertThat(report.failures())
                    .noneMatch(message -> message.contains("扫码记录"));
            Integer pendingScans = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM scan_record WHERE chain_tx_hash IS NULL
                    """, Integer.class);
            assertThat(pendingScans).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM scan_record");
            jdbcTemplate.update("""
                    UPDATE trace_code SET chain_tx_hash = ?
                    WHERE product_code = 'GH-TRACE-2026-0001'
                    """, "0x" + "2".repeat(64));
        }
    }
}
