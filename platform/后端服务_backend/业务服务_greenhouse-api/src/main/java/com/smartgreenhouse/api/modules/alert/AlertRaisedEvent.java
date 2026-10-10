package com.smartgreenhouse.api.modules.alert;

/**
 * 告警落库事件：在事务提交后用于触发短信预警等外部动作。
 */
public record AlertRaisedEvent(
        Long greenhouseId,
        String metricCode,
        String level,
        String message
) {
}
