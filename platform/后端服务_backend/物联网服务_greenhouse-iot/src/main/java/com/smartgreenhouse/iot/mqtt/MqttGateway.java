package com.smartgreenhouse.iot.mqtt;

/**
 * MQTT 网关边界，可替换为 Eclipse Paho 或 Spring Integration MQTT 实现。
 */
public interface MqttGateway {

    void connect();

    void subscribe(String topic);

    void publish(String topic, byte[] payload);
}
