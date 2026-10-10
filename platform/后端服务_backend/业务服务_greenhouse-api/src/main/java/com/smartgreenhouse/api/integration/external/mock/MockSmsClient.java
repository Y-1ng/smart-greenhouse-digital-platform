package com.smartgreenhouse.api.integration.external.mock;

import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.SmsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 短信预警模拟实现：只生成模拟消息编号，不真正发短信。
 */
@Component
@ConditionalOnProperty(
        name = "app.external.sms.mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockSmsClient implements SmsClient {

    @Override
    public ExternalCallResult sendAlert(String phone, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", "SMS-" + UUID.randomUUID());
        payload.put("phone", mask(phone));
        payload.put("content", message);
        payload.put("sentAt", LocalDateTime.now().withNano(0).toString());
        return ExternalCallResult.ok("预警短信已发送（模拟）", payload);
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3)
                + "****"
                + phone.substring(phone.length() - 4);
    }

    @Override
    public String mode() {
        return "mock";
    }
}
