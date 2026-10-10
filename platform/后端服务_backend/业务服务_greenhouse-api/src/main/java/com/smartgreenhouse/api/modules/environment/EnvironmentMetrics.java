package com.smartgreenhouse.api.modules.environment;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 环境周期存证覆盖的指标定义。
 *
 * <p>温度、湿度、光照、土壤水分四项读数全部到齐后才会生成环境周期摘要与根哈希，
 * 保证链上存证覆盖完整的作物环境画像，不完整的周期直接拒绝存证。
 */
public final class EnvironmentMetrics {

    /** 环境周期必须覆盖的四项指标，顺序即消费者端展示顺序。 */
    public static final List<String> REQUIRED_CODES = List.of(
            "temperature",
            "humidity",
            "light",
            "soil_moisture"
    );

    private static final Map<String, String> LABELS = Map.of(
            "temperature", "温度",
            "humidity", "湿度",
            "light", "光照",
            "soil_moisture", "土壤水分"
    );

    private EnvironmentMetrics() {
    }

    public static String normalize(String metricCode) {
        return metricCode == null
                ? ""
                : metricCode.trim().toLowerCase(Locale.ROOT);
    }

    /** 返回缺失的必填指标，全部到齐时返回空列表。 */
    public static List<String> missingCodes(Collection<String> presentCodes) {
        Set<String> present = new LinkedHashSet<>();
        if (presentCodes != null) {
            presentCodes.forEach(code -> present.add(normalize(code)));
        }
        return REQUIRED_CODES.stream()
                .filter(code -> !present.contains(code))
                .toList();
    }

    public static String labelOf(String metricCode) {
        String normalized = normalize(metricCode);
        return LABELS.getOrDefault(normalized, normalized);
    }

    public static String labelsOf(Collection<String> metricCodes) {
        return metricCodes.stream()
                .map(EnvironmentMetrics::labelOf)
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }
}
