package com.smartgreenhouse.api.modules.environment;

import com.smartgreenhouse.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 演示环境必须四项指标齐全才能生成环境周期，避免链上存证缺项。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:environment_required_metrics_test;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class EnvironmentCycleRequiredMetricsTest {

    @Autowired
    private EnvironmentService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsTelemetryMissingSoilMoisture() {
        insertTelemetry("temperature", "23.0000", "℃", "2026-09-18 14:40:00");
        insertTelemetry("humidity", "67.0000", "%", "2026-09-18 14:40:00");
        insertTelemetry("light", "18000.0000", "lx", "2026-09-18 14:40:00");

        assertThatThrownBy(() -> service.attestFromTelemetry("GH-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("土壤水分");

        Integer cycleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM environment_cycle WHERE greenhouse_id = 1",
                Integer.class
        );
        assertThat(cycleCount).isEqualTo(1);
    }

    private void insertTelemetry(
            String metricCode,
            String value,
            String unit,
            String collectedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO iot_telemetry (
                  greenhouse_id,
                  device_id,
                  metric_code,
                  metric_value,
                  unit,
                  collected_at,
                  raw_topic
                ) VALUES (1, 1, ?, ?, ?, ?, ?)
                """,
                metricCode,
                value,
                unit,
                collectedAt,
                "iot/GH-001/FAN-A-02/telemetry"
        );
    }
}
