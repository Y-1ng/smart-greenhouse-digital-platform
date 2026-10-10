package com.smartgreenhouse.api.modules.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 链上补偿定时任务：周期检查待补偿存证，有则重试上链。
 * 无待补偿记录时不产生任何链上调用，测试配置通过 app.chain.compensation.enabled=false 关闭。
 */
@Component
@ConditionalOnProperty(
        name = "app.chain.compensation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChainCompensationJob {

    private static final Logger log =
            LoggerFactory.getLogger(ChainCompensationJob.class);

    private final ChainCompensationService service;

    public ChainCompensationJob(ChainCompensationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.chain.compensation.interval-ms:60000}",
            initialDelayString = "${app.chain.compensation.initial-delay-ms:30000}"
    )
    public void retryPending() {
        ChainPendingView pending = service.countPending();
        if (pending.total() == 0) {
            return;
        }
        log.info("检测到 {} 条待补偿存证，开始重试", pending.total());
        ChainCompensationReport report = service.compensate();
        log.info(
                "待补偿重试完成：成功 {} 条，失败 {} 条",
                report.confirmed(),
                report.failed()
        );
    }
}
