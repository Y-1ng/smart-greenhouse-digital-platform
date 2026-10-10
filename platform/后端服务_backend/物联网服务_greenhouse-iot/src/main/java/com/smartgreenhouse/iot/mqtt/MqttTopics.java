package com.smartgreenhouse.iot.mqtt;

/**
 * MQTT 主题解析工具，主题格式为 iot/{greenhouseCode}/{deviceCode}/{action}。
 */
final class MqttTopics {

    private MqttTopics() {
    }

    static String part(String topic, int index) {
        if (topic == null) {
            return "";
        }
        String[] parts = topic.split("/");
        return index < parts.length ? parts[index] : "";
    }
}
