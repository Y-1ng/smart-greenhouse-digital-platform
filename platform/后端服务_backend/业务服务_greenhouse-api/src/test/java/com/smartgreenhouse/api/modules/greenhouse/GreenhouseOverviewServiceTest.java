package com.smartgreenhouse.api.modules.greenhouse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class GreenhouseOverviewServiceTest {

    @Autowired
    private GreenhouseOverviewService service;

    @Test
    void returnsAggregatedOverviewForSelectedGreenhouse() {
        GreenhouseOverview overview = service.getOverview("GH-001");

        assertThat(overview.greenhouseName()).isEqualTo("1号温室");
        assertThat(overview.cropName()).isEqualTo("番茄");
        assertThat(overview.varietyName()).isEqualTo("金棚一号");
        assertThat(overview.rootHash()).isEqualTo("a".repeat(64));
        assertThat(overview.metrics())
                .containsEntry("temperature", new BigDecimal("22.6000"))
                .containsEntry("humidity", new BigDecimal("68.4000"))
                .containsEntry("light", new BigDecimal("18500.0000"))
                .containsEntry("soil_moisture", new BigDecimal("43.2000"));
        assertThat(overview.devices())
                .extracting(GreenhouseOverview.DeviceSnapshot::deviceCode)
                .containsExactly(
                        "FAN-A-02",
                        "VALVE-B-01",
                        "LIGHT-A-03",
                        "CURTAIN-A-01"
                );
        assertThat(overview.devices())
                .extracting(GreenhouseOverview.DeviceSnapshot::operatingState)
                .containsExactly("ON", "OFF", "OFF", "ON");
        assertThat(overview.devices())
                .extracting(GreenhouseOverview.DeviceSnapshot::onlineStatus)
                .containsOnly("ONLINE");
    }
}
