package com.smartgreenhouse.api.modules.ai;

import com.smartgreenhouse.api.modules.alert.AlertService;
import com.smartgreenhouse.api.modules.alert.AlertView;
import com.smartgreenhouse.api.modules.dashboard.DashboardService;
import com.smartgreenhouse.api.modules.dashboard.GovernmentDashboardView;
import com.smartgreenhouse.api.modules.environment.EnvironmentService;
import com.smartgreenhouse.api.modules.environment.EnvironmentSnapshot;
import com.smartgreenhouse.api.modules.environment.EnvironmentThresholdService;
import com.smartgreenhouse.api.modules.environment.EnvironmentThresholdView;
import com.smartgreenhouse.api.modules.greenhouse.GreenhouseOverviewService;
import com.smartgreenhouse.api.modules.greenhouse.GreenhouseSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把平台真实数据压缩成一段可读的上下文，交给大模型做归纳。
 * 这里只读现有服务，不新增统计口径；任何一段取数失败都只跳过该段，
 * 不会让整个 AI 接口不可用。
 */
@Service
public class AiPlatformContextService {

    private static final Logger log =
            LoggerFactory.getLogger(AiPlatformContextService.class);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 明细里最多列几座温室，避免提示词过长 */
    private static final int MAX_GREENHOUSES = 4;
    /** 最多列几条待处理预警 */
    private static final int MAX_ALERTS = 5;

    private static final Map<String, String> METRIC_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> METRIC_NAMES = new LinkedHashMap<>();

    static {
        METRIC_LABELS.put("temperature", "温度℃");
        METRIC_LABELS.put("humidity", "湿度%");
        METRIC_LABELS.put("light", "光照klx");
        METRIC_LABELS.put("soil_moisture", "土壤水分%");
        METRIC_NAMES.put("temperature", "温度");
        METRIC_NAMES.put("humidity", "湿度");
        METRIC_NAMES.put("light", "光照");
        METRIC_NAMES.put("soil_moisture", "土壤水分");
    }

    private final DashboardService dashboardService;
    private final GreenhouseOverviewService greenhouseOverviewService;
    private final EnvironmentService environmentService;
    private final EnvironmentThresholdService thresholdService;
    private final AlertService alertService;

    public AiPlatformContextService(
            DashboardService dashboardService,
            GreenhouseOverviewService greenhouseOverviewService,
            EnvironmentService environmentService,
            EnvironmentThresholdService thresholdService,
            AlertService alertService
    ) {
        this.dashboardService = dashboardService;
        this.greenhouseOverviewService = greenhouseOverviewService;
        this.environmentService = environmentService;
        this.thresholdService = thresholdService;
        this.alertService = alertService;
    }

    public PlatformContext collect() {
        StringBuilder text = new StringBuilder();
        List<String> sections = new ArrayList<>();
        List<String> highlights = new ArrayList<>();

        text.append("数据采集时间：")
                .append(LocalDateTime.now().format(TIME_FORMAT))
                .append('\n');

        appendOverview(text, sections, highlights);
        appendGreenhouses(text, sections, highlights);
        appendAlerts(text, sections, highlights);

        return new PlatformContext(text.toString(), sections, highlights);
    }

    private void appendOverview(
            StringBuilder text,
            List<String> sections,
            List<String> highlights
    ) {
        try {
            GovernmentDashboardView dashboard =
                    dashboardService.governmentDashboard();
            text.append("园区概况：温室 ")
                    .append(dashboard.greenhouseCount())
                    .append(" 座；设备在线率 ")
                    .append(percent(dashboard.deviceOnlineRate()))
                    .append("；溯源覆盖率 ")
                    .append(percent(dashboard.traceabilityCoverage()))
                    .append("；待处理预警 ")
                    .append(dashboard.activeAlerts())
                    .append(" 条；离线设备 ")
                    .append(dashboard.offlineDeviceCount())
                    .append(" 台；链上待确认 ")
                    .append(dashboard.pendingChainProofCount())
                    .append(" 条；重复扫码 ")
                    .append(dashboard.repeatScanCount())
                    .append(" 次\n");
            sections.add("园区概况");
            highlights.add("温室 " + dashboard.greenhouseCount() + " 座");
            highlights.add("设备在线率 " + percent(dashboard.deviceOnlineRate()));
            highlights.add("待处理预警 " + dashboard.activeAlerts() + " 条");
        } catch (Exception exception) {
            log.warn("AI 上下文：园区概况读取失败", exception);
        }
    }

    private void appendGreenhouses(
            StringBuilder text,
            List<String> sections,
            List<String> highlights
    ) {
        try {
            List<GreenhouseSummary> greenhouses =
                    greenhouseOverviewService.listGreenhouses();
            if (greenhouses.isEmpty()) {
                return;
            }
            text.append("温室明细：\n");
            boolean hasThresholds = false;
            for (GreenhouseSummary greenhouse
                    : greenhouses.subList(0, Math.min(MAX_GREENHOUSES, greenhouses.size()))) {
                text.append("- ")
                        .append(greenhouse.greenhouseCode())
                        .append(' ')
                        .append(greenhouse.greenhouseName());
                if (greenhouse.cropName() != null) {
                    text.append("（").append(greenhouse.cropName());
                    if (greenhouse.varietyName() != null) {
                        text.append('/').append(greenhouse.varietyName());
                    }
                    text.append('）');
                }
                if (greenhouse.area() != null) {
                    text.append("，面积 ").append(plain(greenhouse.area())).append("㎡");
                }
                if (greenhouse.location() != null) {
                    text.append("，位置 ").append(greenhouse.location());
                }
                EnvironmentBrief brief = environmentBrief(greenhouse.greenhouseCode());
                text.append(brief.text());
                hasThresholds = hasThresholds || brief.hasThresholds();
                text.append('\n');
            }
            if (greenhouses.size() > MAX_GREENHOUSES) {
                text.append("- 其余 ")
                        .append(greenhouses.size() - MAX_GREENHOUSES)
                        .append(" 座温室未逐条列出\n");
            }
            sections.add("温室与环境明细");
            if (hasThresholds) {
                sections.add("目标区间与联动");
            }
            highlights.add("温室明细 " + greenhouses.size() + " 座");
        } catch (Exception exception) {
            log.warn("AI 上下文：温室明细读取失败", exception);
        }
    }

    private EnvironmentBrief environmentBrief(String greenhouseCode) {
        try {
            EnvironmentSnapshot snapshot =
                    environmentService.getLatest(greenhouseCode);
            if (snapshot.metrics().isEmpty()) {
                return new EnvironmentBrief("；暂无环境采样", false);
            }
            StringBuilder brief = new StringBuilder("；");
            boolean first = true;
            for (Map.Entry<String, BigDecimal> entry
                    : snapshot.metrics().entrySet()) {
                if (!first) {
                    brief.append('、');
                }
                first = false;
                brief.append(METRIC_LABELS.getOrDefault(
                                entry.getKey(),
                                entry.getKey()
                        ))
                        .append(' ')
                        .append(plain(entry.getValue()));
            }
            // 带上目标区间，模型才能算"偏离多少"；没有阈值时这段为空
            String thresholdText = thresholdBrief(greenhouseCode);
            brief.append(thresholdText);
            if (snapshot.collectedAt() != null) {
                brief.append("，采集于 ")
                        .append(snapshot.collectedAt().format(TIME_FORMAT));
            }
            if (snapshot.chainStatus() != null) {
                brief.append("，环境周期链状态 ").append(snapshot.chainStatus());
            }
            return new EnvironmentBrief(
                    brief.toString(),
                    !thresholdText.isEmpty()
            );
        } catch (Exception exception) {
            log.warn("AI 上下文：温室 {} 环境读取失败", greenhouseCode, exception);
            return new EnvironmentBrief("；环境数据读取失败", false);
        }
    }

    private record EnvironmentBrief(String text, boolean hasThresholds) {
    }

    /**
     * 目标区间与联动动作。之前 AI 只知道当前读数、不知道目标范围，
     * 问"哪座温室温度偏离最大"只能回答"平台没有目标区间"。
     */
    private String thresholdBrief(String greenhouseCode) {
        try {
            List<EnvironmentThresholdView> thresholds =
                    thresholdService.list(greenhouseCode);
            StringBuilder brief = new StringBuilder();
            for (EnvironmentThresholdView threshold : thresholds) {
                if (Boolean.FALSE.equals(threshold.enabled())) {
                    continue;
                }
                brief.append(brief.isEmpty() ? "，目标区间 " : "、")
                        .append(METRIC_NAMES.getOrDefault(
                                threshold.metricCode(),
                                threshold.metricCode()
                        ))
                        .append(' ')
                        .append(plain(threshold.targetMin()))
                        .append('–')
                        .append(plain(threshold.targetMax()));
            }
            return brief.toString();
        } catch (Exception exception) {
            log.warn("AI 上下文：温室 {} 阈值读取失败", greenhouseCode, exception);
            return "";
        }
    }

    private void appendAlerts(
            StringBuilder text,
            List<String> sections,
            List<String> highlights
    ) {
        try {
            List<AlertView> alerts = alertService.list(null, "PENDING", MAX_ALERTS);
            if (alerts.isEmpty()) {
                text.append("待处理预警：无\n");
                return;
            }
            text.append("待处理预警：\n");
            for (AlertView alert : alerts) {
                text.append("- ")
                        .append(alert.greenhouseCode())
                        .append(' ')
                        .append(alert.metricCode())
                        .append(' ')
                        .append(plain(alert.metricValue()))
                        .append("（阈值 ")
                        .append(plain(alert.alertMin()))
                        .append('~')
                        .append(plain(alert.alertMax()))
                        .append("，等级 ")
                        .append(alert.alertLevel())
                        .append('）');
                if (alert.message() != null) {
                    text.append(' ').append(alert.message());
                }
                text.append('\n');
            }
            sections.add("待处理预警");
            highlights.add("待处理预警 " + alerts.size() + " 条");
        } catch (Exception exception) {
            log.warn("AI 上下文：预警读取失败", exception);
        }
    }

    private String plain(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "0%";
        }
        return ratio.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    /**
     * @param text       直接拼进提示词的多行文本
     * @param sections   本次真正取到数的数据块名称，用于前端展示"模型看得到什么"
     * @param highlights 关键指标摘要，用于前端顶部提示
     */
    public record PlatformContext(
            String text,
            List<String> sections,
            List<String> highlights
    ) {
    }
}
