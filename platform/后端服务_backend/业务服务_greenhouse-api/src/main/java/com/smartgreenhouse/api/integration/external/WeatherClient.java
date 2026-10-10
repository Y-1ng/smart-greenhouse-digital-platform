package com.smartgreenhouse.api.integration.external;

/**
 * 气象服务适配器：拉取温室所在位置的短临预报，用于控制策略与预警。
 */
public interface WeatherClient {

    ExternalCallResult fetchForecast(String greenhouseCode, String location);

    String mode();
}
