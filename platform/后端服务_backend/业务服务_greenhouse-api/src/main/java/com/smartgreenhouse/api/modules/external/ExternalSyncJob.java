package com.smartgreenhouse.api.modules.external;

import com.smartgreenhouse.api.integration.external.ExternalPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 气象数据定时同步：默认每 10 分钟拉取一次各基地预报，结果写入 external_sync_log。
 */
@Component
@ConditionalOnProperty(
        name = "app.external.weather.sync-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExternalSyncJob {

    private static final Logger log =
            LoggerFactory.getLogger(ExternalSyncJob.class);

    private final ExternalSyncService service;

    public ExternalSyncJob(ExternalSyncService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.external.weather.sync-interval-ms:600000}",
            initialDelayString = "${app.external.weather.sync-initial-delay-ms:45000}"
    )
    public void syncWeather() {
        ExternalViews.ExternalSyncReport report =
                service.sync(ExternalPlatform.WEATHER);
        log.info(
                "气象数据定时同步完成：成功 {} 条，失败 {} 条",
                report.succeeded(),
                report.failed()
        );
    }
}
