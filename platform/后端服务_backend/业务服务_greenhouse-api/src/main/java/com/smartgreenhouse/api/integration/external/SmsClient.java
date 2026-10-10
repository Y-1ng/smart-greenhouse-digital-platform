package com.smartgreenhouse.api.integration.external;

/**
 * 短信预警适配器：把高危环境预警推送给基地负责人。
 */
public interface SmsClient {

    ExternalCallResult sendAlert(String phone, String message);

    String mode();
}
