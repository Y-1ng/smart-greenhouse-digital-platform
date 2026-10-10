package com.smartgreenhouse.api.modules.external;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 外部平台对接相关的视图对象。
 */
public final class ExternalViews {

    private ExternalViews() {
    }

    /**
     * 单个平台的对接状态：模式、最近一次结果、累计成功/失败次数。
     */
    public record ExternalStatusView(
            String platform,
            String label,
            String mode,
            String lastStatus,
            String lastMessage,
            LocalDateTime lastAt,
            int successCount,
            int failureCount
    ) {
    }

    public record ExternalLogView(
            Long logId,
            String platform,
            String label,
            String requestNo,
            String status,
            int attempts,
            long durationMs,
            String mode,
            String message,
            Map<String, Object> payload,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 一次手动同步的结果汇总。
     */
    public record ExternalSyncReport(
            String platform,
            String label,
            int succeeded,
            int failed,
            List<ExternalLogView> entries
    ) {
    }
}
