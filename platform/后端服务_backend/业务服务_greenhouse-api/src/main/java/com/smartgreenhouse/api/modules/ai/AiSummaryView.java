package com.smartgreenhouse.api.modules.ai;

import java.time.Instant;
import java.util.List;

/**
 * AI 总结结果。
 *
 * @param source          deepseek=大模型输出；local=未配置密钥或调用失败时的本地框架结论
 * @param notice          降级原因，正常生成时为 null
 * @param contextSections 本次真正喂给模型的数据块名称
 * @param highlights      关键指标摘要，供界面直接展示
 */
public record AiSummaryView(
        String content,
        String source,
        String provider,
        String model,
        String frameworkId,
        String framework,
        String scopeTitle,
        long latencyMs,
        String notice,
        List<String> contextSections,
        List<String> highlights,
        Instant generatedAt
) {
}
