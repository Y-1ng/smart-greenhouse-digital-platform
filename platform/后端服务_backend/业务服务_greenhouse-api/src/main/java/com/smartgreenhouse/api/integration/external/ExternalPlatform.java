package com.smartgreenhouse.api.integration.external;

/**
 * 外部平台类型：京东农场（产销对接）、气象服务、短信预警。
 */
public enum ExternalPlatform {

    JD_FARM("京东农场", "app.external.marketplace"),
    WEATHER("气象服务", "app.external.weather"),
    SMS("短信预警", "app.external.sms");

    private final String label;
    private final String configPrefix;

    ExternalPlatform(String label, String configPrefix) {
        this.label = label;
        this.configPrefix = configPrefix;
    }

    public String label() {
        return label;
    }

    public String configPrefix() {
        return configPrefix;
    }
}
