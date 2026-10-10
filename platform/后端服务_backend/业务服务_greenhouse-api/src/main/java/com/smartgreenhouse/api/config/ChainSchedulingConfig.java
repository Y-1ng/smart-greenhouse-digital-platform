package com.smartgreenhouse.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 补偿任务不依赖 demo 配置，因此在通用配置中开启定时调度。
 * 自动化测试通过 app.chain.compensation.enabled=false 关闭任务本身。
 */
@Configuration
@EnableScheduling
public class ChainSchedulingConfig {
}
