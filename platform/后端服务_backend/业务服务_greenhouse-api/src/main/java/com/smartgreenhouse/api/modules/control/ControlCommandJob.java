package com.smartgreenhouse.api.modules.control;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 控制指令超时扫描：设备长时间不回应答时先重试，超过重试上限置为失败。
 * 测试配置通过 app.control.timeout-scan.enabled=false 关闭。
 */
@Component
@ConditionalOnProperty(
        name = "app.control.timeout-scan.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ControlCommandJob {

    private final ControlCommandService service;

    public ControlCommandJob(ControlCommandService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.control.timeout-scan.interval-ms:10000}",
            initialDelayString = "${app.control.timeout-scan.initial-delay-ms:15000}"
    )
    public void scanTimeouts() {
        service.applyTimeouts();
    }
}
