package com.smartgreenhouse.api.integration.external;

import java.util.Map;

/**
 * 外部平台调用结果。失败用 success=false + message 表达，不抛异常给业务流程。
 */
public record ExternalCallResult(
        boolean success,
        String message,
        Map<String, Object> payload
) {

    public static ExternalCallResult ok(String message, Map<String, Object> payload) {
        return new ExternalCallResult(true, message, payload);
    }

    public static ExternalCallResult failure(String message) {
        return new ExternalCallResult(false, message, Map.of());
    }
}
