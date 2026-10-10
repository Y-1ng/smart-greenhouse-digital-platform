package com.smartgreenhouse.iot.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TelemetryIngestService {

    private static final Logger log =
            LoggerFactory.getLogger(TelemetryIngestService.class);

    private final ObjectMapper objectMapper;
    private final TelemetryForwarder forwarder;

    public TelemetryIngestService(
            ObjectMapper objectMapper,
            TelemetryForwarder forwarder
    ) {
        this.objectMapper = objectMapper;
        this.forwarder = forwarder;
    }

    public void accept(String topic, byte[] payload) {
        try {
            List<TelemetryMessage> messages = parse(topic, payload);
            messages.forEach(forwarder::forward);
            log.info(
                    "Forwarded {} telemetry readings from {}",
                    messages.size(),
                    topic
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to process telemetry from topic {}",
                    topic,
                    exception
            );
        }
    }

    List<TelemetryMessage> parse(String topic, byte[] payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode readings = root.isArray() ? root : root.path("readings");
        List<TelemetryMessage> messages = new ArrayList<>();
        if (readings.isArray() && !readings.isEmpty()) {
            for (JsonNode reading : readings) {
                messages.add(toMessage(topic, reading, root));
            }
            return messages;
        }
        messages.add(toMessage(topic, root, root));
        return messages;
    }

    private TelemetryMessage toMessage(
            String topic,
            JsonNode reading,
            JsonNode root
    ) {
        String topicGreenhouse = topicPart(topic, 1);
        String topicDevice = topicPart(topic, 2);
        return new TelemetryMessage(
                text(reading, root, "greenhouseCode", topicGreenhouse),
                text(reading, root, "deviceCode", topicDevice),
                requiredText(reading, "metricCode"),
                new BigDecimal(requiredText(reading, "value")),
                text(reading, root, "unit", null),
                LocalDateTime.parse(requiredText(reading, "collectedAt")),
                text(reading, root, "topic", topic)
        );
    }

    private String text(
            JsonNode primary,
            JsonNode fallback,
            String field,
            String defaultValue
    ) {
        if (primary.hasNonNull(field)) {
            return primary.get(field).asText();
        }
        if (fallback != primary && fallback.hasNonNull(field)) {
            return fallback.get(field).asText();
        }
        return defaultValue;
    }

    private String requiredText(JsonNode node, String field) {
        if (!node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing telemetry field: " + field
            );
        }
        return node.get(field).asText();
    }

    private String topicPart(String topic, int index) {
        String[] parts = topic.split("/");
        return parts.length > index ? parts[index] : null;
    }
}
