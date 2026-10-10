package com.smartgreenhouse.api.modules.alert;

import com.smartgreenhouse.api.modules.environment.EnvironmentThresholdMapper;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final Map<String, String> METRIC_LABELS = Map.of(
            "temperature", "空气温度",
            "humidity", "空气湿度",
            "light", "光照强度",
            "soil_moisture", "土壤水分"
    );

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AlertMapper mapper;
    private final EnvironmentThresholdMapper thresholdMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AlertService(
            AlertMapper mapper,
            EnvironmentThresholdMapper thresholdMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.mapper = mapper;
        this.thresholdMapper = thresholdMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 按阈值判定一条遥测读数，超出告警范围且当前没有待处理预警时落库。
     */
    @Transactional
    public void evaluate(
            Long greenhouseId,
            String metricCode,
            BigDecimal value,
            LocalDateTime collectedAt
    ) {
        EnvironmentThresholdMapper.ThresholdRow threshold =
                thresholdMapper.findOne(greenhouseId, metricCode);
        if (threshold == null || !Boolean.TRUE.equals(threshold.enabled())) {
            return;
        }
        if (value.compareTo(threshold.alertMin()) >= 0
                && value.compareTo(threshold.alertMax()) <= 0) {
            return;
        }
        if (mapper.countPending(greenhouseId, metricCode) > 0) {
            return;
        }

        boolean low = value.compareTo(threshold.alertMin()) < 0;
        String level = low ? "LOW" : "HIGH";
        BigDecimal bound = low ? threshold.alertMin() : threshold.alertMax();
        String label = METRIC_LABELS.getOrDefault(metricCode, metricCode);
        String message = label
                + " " + plain(value)
                + (low ? " 低于告警下限 " : " 高于告警上限 ")
                + plain(bound);

        mapper.insert(new AlertMapper.AlertInsert(
                greenhouseId,
                metricCode,
                value,
                level,
                threshold.alertMin(),
                threshold.alertMax(),
                message,
                collectedAt == null ? LocalDateTime.now() : collectedAt
        ));
        log.info("已记录预警：{}", message);
        // 事务提交后触发短信通知（见 ExternalAlertListener）
        eventPublisher.publishEvent(new AlertRaisedEvent(
                greenhouseId,
                metricCode,
                level,
                message
        ));
    }

    @Transactional(readOnly = true)
    public List<AlertView> list(
            String greenhouseCode,
            String status,
            Integer limit
    ) {
        return mapper.findAlerts(
                        greenhouseCode,
                        status,
                        normalizeLimit(limit)
                ).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AlertView acknowledge(Long alertId, String operator) {
        if (mapper.findOne(alertId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预警记录不存在");
        }
        mapper.acknowledge(alertId, operator, LocalDateTime.now());
        return toView(mapper.findOne(alertId));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private AlertView toView(AlertMapper.AlertRow row) {
        return new AlertView(
                row.alertId(),
                row.greenhouseCode(),
                row.metricCode(),
                row.metricValue(),
                row.alertLevel(),
                row.alertMin(),
                row.alertMax(),
                row.message(),
                row.status(),
                row.createdAt(),
                row.acknowledgedAt(),
                row.acknowledgedBy()
        );
    }
}
