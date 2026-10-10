package com.smartgreenhouse.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 仅演示环境开启定时任务，用于周期性注入演示遥测。
 */
@Configuration
@Profile("demo")
@EnableScheduling
public class DemoSchedulingConfig {
}
