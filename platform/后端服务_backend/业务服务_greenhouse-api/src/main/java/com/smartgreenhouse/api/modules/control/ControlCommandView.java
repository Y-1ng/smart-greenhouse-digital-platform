package com.smartgreenhouse.api.modules.control;

import java.time.LocalDateTime;
import java.util.Map;

public record ControlCommandView(
        String commandNo,
        String greenhouseCode,
        String deviceCode,
        String commandType,
        Map<String, Object> parameters,
        String operator,
        int timeoutSeconds,
        String status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime dispatchedAt,
        LocalDateTime acknowledgedAt,
        LocalDateTime completedAt,
        int retryCount
) {
}
