package com.smartgreenhouse.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 连接、主题和 QoS 配置。
 */
@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {

    private String mode = "logging";
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "greenhouse-iot-service";
    private String username = "";
    private String password = "";
    private String telemetryTopic = "iot/+/+/telemetry";
    private String statusTopic = "iot/+/+/status";
    private String commandTopicTemplate = "iot/%s/%s/command";
    private String ackTopic = "iot/+/+/ack";
    private int qos = 1;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTelemetryTopic() {
        return telemetryTopic;
    }

    public void setTelemetryTopic(String telemetryTopic) {
        this.telemetryTopic = telemetryTopic;
    }

    public String getStatusTopic() {
        return statusTopic;
    }

    public void setStatusTopic(String statusTopic) {
        this.statusTopic = statusTopic;
    }

    public String getCommandTopicTemplate() {
        return commandTopicTemplate;
    }

    public void setCommandTopicTemplate(String commandTopicTemplate) {
        this.commandTopicTemplate = commandTopicTemplate;
    }

    public String getAckTopic() {
        return ackTopic;
    }

    public void setAckTopic(String ackTopic) {
        this.ackTopic = ackTopic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }
}
