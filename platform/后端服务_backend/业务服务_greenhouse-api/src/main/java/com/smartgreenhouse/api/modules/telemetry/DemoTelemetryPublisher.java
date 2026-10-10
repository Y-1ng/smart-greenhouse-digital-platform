package com.smartgreenhouse.api.modules.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示环境下按固定间隔注入设备遥测，等价于设备周期上报。
 * 仅当 spring.profiles.active 包含 demo 且 app.demo.telemetry.enabled 未关闭时生效。
 */
@Component
@Profile("demo")
@ConditionalOnProperty(
        name = "app.demo.telemetry.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DemoTelemetryPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(DemoTelemetryPublisher.class);

    private static final List<MetricProfile> PROFILES = List.of(
            new MetricProfile("temperature", "C", 22.5, 2.6, 240),
            new MetricProfile("humidity", "%", 68.0, 4.0, 300),
            new MetricProfile("light", "lx", 18600, 3200, 180),
            new MetricProfile("soil_moisture", "%", 43.0, 3.2, 360)
    );

    private final IotTelemetryMapper mapper;
    private final IotTelemetryService telemetryService;
    private final List<String> configuredGreenhouseCodes;
    private final boolean alertDemo;
    private final long startedAt = System.currentTimeMillis();

    private final Map<String, String> deviceCodes = new LinkedHashMap<>();
    private long preparedAt;
    private boolean logged;

    public DemoTelemetryPublisher(
            IotTelemetryMapper mapper,
            IotTelemetryService telemetryService,
            @Value("${app.demo.telemetry.greenhouse-codes:}")
            String greenhouseCodes,
            @Value("${app.demo.telemetry.alert-demo:false}")
            boolean alertDemo
    ) {
        this.mapper = mapper;
        this.telemetryService = telemetryService;
        this.configuredGreenhouseCodes = parseCodes(greenhouseCodes);
        this.alertDemo = alertDemo;
    }

    @Scheduled(
            fixedDelayString = "${app.demo.telemetry.interval-ms:15000}",
            initialDelayString = "${app.demo.telemetry.initial-delay-ms:6000}"
    )
    @Transactional
    public void publish() {
        if (!prepare()) {
            return;
        }

        for (Map.Entry<String, String> entry : deviceCodes.entrySet()) {
            publishGreenhouse(entry.getKey(), entry.getValue());
        }

        if (!logged) {
            logged = true;
            log.info(
                    "演示遥测已开始注入：{} 座温室，可通过 app.demo.telemetry.* 调整",
                    deviceCodes.size()
            );
        }
    }

    private boolean prepare() {
        long now = System.currentTimeMillis();
        if (!deviceCodes.isEmpty() && now - preparedAt < 60_000) {
            return true;
        }

        List<String> codes = configuredGreenhouseCodes.isEmpty()
                ? mapper.findAllGreenhouseCodes()
                : configuredGreenhouseCodes;
        Map<String, String> next = new LinkedHashMap<>();
        for (String greenhouseCode : codes) {
            IotTelemetryMapper.GreenhouseRow greenhouse =
                    mapper.findGreenhouseByCode(greenhouseCode);
            if (greenhouse == null) {
                continue;
            }
            String code = mapper.findFirstDeviceCode(greenhouse.greenhouseId());
            if (code == null
                    || mapper.findDeviceId(greenhouse.greenhouseId(), code)
                    == null) {
                continue;
            }
            next.put(greenhouseCode, code);
        }

        deviceCodes.clear();
        deviceCodes.putAll(next);
        preparedAt = now;
        return !deviceCodes.isEmpty();
    }

    private void publishGreenhouse(String greenhouseCode, String deviceCode) {
        long elapsedSeconds =
                (System.currentTimeMillis() - startedAt) / 1000;
        LocalDateTime collectedAt = LocalDateTime.now().withNano(0);
        String topic = "iot/" + greenhouseCode + "/" + deviceCode + "/telemetry";

        for (MetricProfile profile : PROFILES) {
            telemetryService.ingest(new IotTelemetryController.IngestRequest(
                    greenhouseCode,
                    deviceCode,
                    profile.metricCode(),
                    profile.valueAt(elapsedSeconds, alertDemo),
                    profile.unit(),
                    collectedAt,
                    topic
            ));
        }
    }

    private List<String> parseCodes(String value) {
        if (value == null || value.isBlank() || "*".equals(value.trim())) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String part : value.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return codes;
    }

    private record MetricProfile(
            String metricCode,
            String unit,
            double base,
            double amplitude,
            int periodSeconds
    ) {

        BigDecimal valueAt(long elapsedSeconds, boolean alertDemo) {
            double angle = 2 * Math.PI
                    * (elapsedSeconds % periodSeconds)
                    / periodSeconds;
            double value = base + amplitude * Math.sin(angle);
            if (alertDemo && "temperature".equals(metricCode)) {
                value += Math.min(elapsedSeconds / 120.0, 1) * 12;
            }
            return BigDecimal.valueOf(value)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}
