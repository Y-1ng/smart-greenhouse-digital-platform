package com.smartgreenhouse.api.integration.external.mock;

import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.WeatherClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 气象服务模拟实现：按温室编号与日期生成确定性预报，供演示与自动化测试使用。
 */
@Component
@ConditionalOnProperty(
        name = "app.external.weather.mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockWeatherClient implements WeatherClient {

    @Override
    public ExternalCallResult fetchForecast(String greenhouseCode, String location) {
        int seed = Math.abs((greenhouseCode + LocalDate.now()).hashCode());
        double temperature = 18 + (seed % 90) / 10.0;
        double humidity = 45 + (seed % 40);
        double windLevel = 1 + (seed % 4);
        boolean rain = seed % 3 == 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("greenhouseCode", greenhouseCode);
        payload.put("location", location == null ? "示范基地" : location);
        payload.put("date", LocalDate.now().toString());
        payload.put("temperature", temperature);
        payload.put("humidity", humidity);
        payload.put("windLevel", windLevel);
        payload.put("rain", rain);
        payload.put("suggestion", rain
                ? "有降水，建议减小灌溉量并检查通风"
                : "无降水，按计划执行通风与补光");
        return ExternalCallResult.ok("气象预报获取成功（模拟）", payload);
    }

    @Override
    public String mode() {
        return "mock";
    }
}
