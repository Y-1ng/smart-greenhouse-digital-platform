package com.smartgreenhouse.iot.mqtt;

import com.smartgreenhouse.iot.config.MqttProperties;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mqtt.mode", havingValue = "broker")
public class PahoMqttGateway implements MqttGateway, SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(PahoMqttGateway.class);

    private final MqttProperties properties;
    private final MqttMessageRouter messageRouter;
    private MqttClient client;
    private volatile boolean running;

    public PahoMqttGateway(
            MqttProperties properties,
            MqttMessageRouter messageRouter
    ) {
        this.properties = properties;
        this.messageRouter = messageRouter;
    }

    @Override
    public void connect() {
        try {
            client = new MqttClient(
                    properties.getBrokerUrl(),
                    properties.getClientId(),
                    new MemoryPersistence()
            );
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            if (!properties.getUsername().isBlank()) {
                options.setUserName(properties.getUsername());
            }
            if (!properties.getPassword().isBlank()) {
                options.setPassword(properties.getPassword().toCharArray());
            }
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    subscribe(properties.getTelemetryTopic());
                    subscribe(properties.getStatusTopic());
                    subscribe(properties.getAckTopic());
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost", cause);
                }

                @Override
                public void messageArrived(
                        String topic,
                        MqttMessage message
                ) {
                    messageRouter.route(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("MQTT delivery completed: {}", token.isComplete());
                }
            });
            client.connect(options);
        } catch (Exception exception) {
            throw new IllegalStateException("MQTT 连接失败", exception);
        }
    }

    @Override
    public void subscribe(String topic) {
        try {
            client.subscribe(topic, properties.getQos());
            log.info("Subscribed MQTT topic {}", topic);
        } catch (Exception exception) {
            throw new IllegalStateException("MQTT 订阅失败: " + topic, exception);
        }
    }

    @Override
    public void publish(String topic, byte[] payload) {
        try {
            MqttMessage message = new MqttMessage(payload);
            message.setQos(properties.getQos());
            client.publish(topic, message);
        } catch (Exception exception) {
            throw new IllegalStateException("MQTT 发布失败: " + topic, exception);
        }
    }

    @Override
    public void start() {
        connect();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (Exception exception) {
            log.warn("Failed to close MQTT client", exception);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
