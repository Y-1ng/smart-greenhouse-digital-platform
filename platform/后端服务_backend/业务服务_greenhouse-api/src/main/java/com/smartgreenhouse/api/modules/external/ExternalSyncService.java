package com.smartgreenhouse.api.modules.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.ExternalPlatform;
import com.smartgreenhouse.api.integration.external.MarketplaceClient;
import com.smartgreenhouse.api.integration.external.SmsClient;
import com.smartgreenhouse.api.integration.external.WeatherClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 外部平台对接编排：统一重试、留痕（external_sync_log）与状态汇总。
 *
 * 设计要点：外部调用失败不会抛给业务方，只记录失败结果；短信预警在告警事务提交后触发。
 */
@Service
public class ExternalSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(ExternalSyncService.class);

    private static final int MAX_PAYLOAD_SUMMARY = 1000;

    private final WeatherClient weatherClient;
    private final SmsClient smsClient;
    private final MarketplaceClient marketplaceClient;
    private final ExternalSyncLogMapper mapper;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final int syncBatchSize;
    private final String alertPhone;

    public ExternalSyncService(
            WeatherClient weatherClient,
            SmsClient smsClient,
            MarketplaceClient marketplaceClient,
            ExternalSyncLogMapper mapper,
            ObjectMapper objectMapper,
            @Value("${app.external.retry.max-attempts:2}") int maxAttempts,
            @Value("${app.external.sync-batch-size:10}") int syncBatchSize,
            @Value("${app.external.sms.alert-phone:13800000000}") String alertPhone
    ) {
        this.weatherClient = weatherClient;
        this.smsClient = smsClient;
        this.marketplaceClient = marketplaceClient;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.syncBatchSize = Math.max(1, syncBatchSize);
        this.alertPhone = alertPhone;
    }

    public List<ExternalViews.ExternalStatusView> status() {
        return List.of(
                statusOf(ExternalPlatform.JD_FARM),
                statusOf(ExternalPlatform.WEATHER),
                statusOf(ExternalPlatform.SMS)
        );
    }

    public List<ExternalViews.ExternalLogView> logs(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return mapper.findRecent(safeLimit).stream()
                .map(this::toLogView)
                .toList();
    }

    public List<ExternalViews.ExternalSyncReport> syncAll() {
        return List.of(
                sync(ExternalPlatform.WEATHER),
                sync(ExternalPlatform.JD_FARM),
                sync(ExternalPlatform.SMS)
        );
    }

    public ExternalViews.ExternalSyncReport sync(ExternalPlatform platform) {
        return switch (platform) {
            case WEATHER -> syncPerGreenhouse(
                    platform,
                    target -> weatherClient.fetchForecast(
                            target.greenhouseCode(),
                            target.location()
                    )
            );
            case JD_FARM -> syncPerGreenhouse(
                    platform,
                    target -> marketplaceClient.syncProducts(
                            target.greenhouseCode()
                    )
            );
            case SMS -> report(
                    platform,
                    List.of(callWithRetry(
                            platform,
                            "手动测试短信",
                            () -> smsClient.sendAlert(alertPhone, "智慧温室演示：预警通道连通性测试")
                    ))
            );
        };
    }

    /**
     * 高危预警短信：由告警事务提交后的监听器调用，失败只留痕不影响业务。
     */
    public void sendAlertSms(String greenhouseCode, String level, String message) {
        String content = "【智慧温室预警】" + greenhouseCode
                + " " + level + "：" + message;
        ExternalViews.ExternalLogView entry = callWithRetry(
                ExternalPlatform.SMS,
                "预警短信 " + greenhouseCode,
                () -> smsClient.sendAlert(alertPhone, content)
        );
        if (!"SUCCESS".equals(entry.status())) {
            log.warn("预警短信发送失败：{}", entry.message());
        }
    }

    private ExternalViews.ExternalSyncReport syncPerGreenhouse(
            ExternalPlatform platform,
            Function<ExternalSyncLogMapper.GreenhouseTarget, ExternalCallResult> call
    ) {
        List<ExternalViews.ExternalLogView> entries = new ArrayList<>();
        for (ExternalSyncLogMapper.GreenhouseTarget target
                : mapper.findGreenhouseTargets(syncBatchSize)) {
            entries.add(callWithRetry(
                    platform,
                    target.greenhouseCode(),
                    () -> call.apply(target)
            ));
        }
        return report(platform, entries);
    }

    /**
     * 统一重试与留痕：最多尝试 maxAttempts 次，任一次成功即结束。
     */
    private ExternalViews.ExternalLogView callWithRetry(
            ExternalPlatform platform,
            String requestNo,
            Supplier<ExternalCallResult> call
    ) {
        long startedAt = System.currentTimeMillis();
        ExternalCallResult result = ExternalCallResult.failure("未执行");
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            result = call.get();
            if (result.success()) {
                break;
            }
        }
        long durationMs = System.currentTimeMillis() - startedAt;
        String status = result.success() ? "SUCCESS" : "FAILED";
        String payloadSummary = summarize(result.payload());

        mapper.insert(new ExternalSyncLogMapper.ExternalSyncLogInsert(
                platform.name(),
                requestNo,
                status,
                attempts,
                durationMs,
                modeOf(platform),
                result.message(),
                payloadSummary
        ));
        if (!result.success()) {
            log.warn(
                    "{} 调用失败（{} 次尝试）：{}",
                    platform.label(),
                    attempts,
                    result.message()
            );
        }

        return new ExternalViews.ExternalLogView(
                null,
                platform.name(),
                platform.label(),
                requestNo,
                status,
                attempts,
                durationMs,
                modeOf(platform),
                result.message(),
                result.payload(),
                LocalDateTime.now()
        );
    }

    private ExternalViews.ExternalSyncReport report(
            ExternalPlatform platform,
            List<ExternalViews.ExternalLogView> entries
    ) {
        int succeeded = (int) entries.stream()
                .filter(entry -> "SUCCESS".equals(entry.status()))
                .count();
        return new ExternalViews.ExternalSyncReport(
                platform.name(),
                platform.label(),
                succeeded,
                entries.size() - succeeded,
                entries
        );
    }

    private ExternalViews.ExternalStatusView statusOf(ExternalPlatform platform) {
        ExternalSyncLogMapper.ExternalLogRow latest =
                mapper.findLatest(platform.name());
        return new ExternalViews.ExternalStatusView(
                platform.name(),
                platform.label(),
                modeOf(platform),
                latest == null ? "NEVER" : latest.status(),
                latest == null ? "尚未调用" : latest.message(),
                latest == null ? null : latest.createdAt(),
                mapper.countByStatus(platform.name(), "SUCCESS"),
                mapper.countByStatus(platform.name(), "FAILED")
        );
    }

    private String modeOf(ExternalPlatform platform) {
        return switch (platform) {
            case WEATHER -> weatherClient.mode();
            case SMS -> smsClient.mode();
            case JD_FARM -> marketplaceClient.mode();
        };
    }

    private ExternalViews.ExternalLogView toLogView(
            ExternalSyncLogMapper.ExternalLogRow row
    ) {
        return new ExternalViews.ExternalLogView(
                row.logId(),
                row.platform(),
                labelOf(row.platform()),
                row.requestNo(),
                row.status(),
                row.attempts() == null ? 1 : row.attempts(),
                row.durationMs() == null ? 0L : row.durationMs(),
                row.mode(),
                row.message(),
                Map.of("summary", row.payloadSummary() == null
                        ? ""
                        : row.payloadSummary()),
                row.createdAt()
        );
    }

    private String labelOf(String platform) {
        try {
            return ExternalPlatform.valueOf(platform).label();
        } catch (IllegalArgumentException exception) {
            return platform;
        }
    }

    private String summarize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() <= MAX_PAYLOAD_SUMMARY
                    ? json
                    : json.substring(0, MAX_PAYLOAD_SUMMARY) + "…";
        } catch (Exception exception) {
            return String.valueOf(payload);
        }
    }

}
