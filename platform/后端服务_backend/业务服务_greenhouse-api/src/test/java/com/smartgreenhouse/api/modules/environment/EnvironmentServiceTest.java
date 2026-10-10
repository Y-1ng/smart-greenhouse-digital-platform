package com.smartgreenhouse.api.modules.environment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:environment_service_test;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class EnvironmentServiceTest {

    @Autowired
    private EnvironmentService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aggregatesTelemetryAndSubmitsEnvironmentCycle() {
        insertTelemetry(
                "temperature", "23.0000", "℃", "2026-09-18 14:36:00"
        );
        insertTelemetry(
                "humidity", "67.0000", "%", "2026-09-18 14:36:00"
        );
        insertTelemetry(
                "light", "18000.0000", "lx", "2026-09-18 14:36:00"
        );
        insertTelemetry(
                "soil_moisture", "43.5000", "%", "2026-09-18 14:36:00"
        );
        insertTelemetry(
                "temperature", "23.2000", "℃", "2026-09-18 14:37:00"
        );
        insertTelemetry(
                "humidity", "67.5000", "%", "2026-09-18 14:37:00"
        );
        insertTelemetry(
                "light", "18100.0000", "lx", "2026-09-18 14:37:00"
        );
        insertTelemetry(
                "soil_moisture", "43.7000", "%", "2026-09-18 14:37:00"
        );

        EnvironmentCycleView cycle = service.attestFromTelemetry("GH-001");

        assertThat(cycle.cycleId()).isGreaterThan(1001L);
        assertThat(cycle.greenhouseCode()).isEqualTo("GH-001");
        assertThat(cycle.readingCount()).isEqualTo(4);
        assertThat(cycle.rootHash()).hasSize(64);
        assertThat(cycle.dataDigest()).hasSize(64);
        assertThat(cycle.chainStatus()).isEqualTo("CONFIRMED");
        assertThat(cycle.chainTxHash()).startsWith("0x");
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
