package com.smartgreenhouse.iot.mqtt;

import com.smartgreenhouse.iot.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发环境日志网关，用于在不连接 Broker 的情况下验证服务启动。
 */
@Component
@ConditionalOnProperty(
        name = "app.mqtt.mode",
        havingValue = "logging",
        matchIfMissing = true
)
public class LoggingMqttGateway implements MqttGateway, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LoggingMqttGateway.class);

    private final MqttProperties properties;
    private volatile boolean running;

    public LoggingMqttGateway(MqttProperties properties) {
        this.properties = properties;
    }

    @Override
    public void connect() {
        log.info("MQTT development gateway configured for {}", properties.getBrokerUrl());
    }

    @Override
    public void subscribe(String topic) {
        log.info("MQTT development gateway subscribes to {}", topic);
    }

    @Override
    public void publish(String topic, byte[] payload) {
        log.info("MQTT development gateway publishes {} bytes to {}", payload.length, topic);
    }

    @Override
    public void start() {
        connect();
        subscribe(properties.getTelemetryTopic());
        subscribe(properties.getStatusTopic());
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
