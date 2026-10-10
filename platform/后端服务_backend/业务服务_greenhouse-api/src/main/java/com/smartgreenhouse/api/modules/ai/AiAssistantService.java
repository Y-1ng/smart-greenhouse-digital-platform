package com.smartgreenhouse.api.modules.ai;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import com.smartgreenhouse.api.integration.ai.DeepSeekChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 总结与 AI 问答的统一实现。
 *
 * <p>调用链：接口 → 组装真实数据上下文 → DeepSeekChatClient（OpenAI 兼容协议）→ DeepSeek → 回填结果。
 * 没配密钥或调用失败时不抛给前端 500，而是返回 source=local 的本地框架结论，
 * 并把失败原因放进 notice，界面必须照实展示这个来源，不能把本地结论伪装成大模型输出。
 */
@Service
public class AiAssistantService {

    private static final Logger log =
            LoggerFactory.getLogger(AiAssistantService.class);

    private static final String SOURCE_DEEPSEEK = "deepseek";
    private static final String SOURCE_LOCAL = "local";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final int MAX_SESSION_ID_LENGTH = 64;
    private static final int MAX_INSTRUCTION_LENGTH = 200;
    private static final int MAX_OPERATOR_LENGTH = 64;

    private final DeepSeekChatClient chatClient;
    private final AiPlatformContextService contextService;
    private final AiChatMapper chatMapper;
    private final String provider;
    private final String modelLabel;
    private final int historyLimit;
    private final int maxQuestionLength;

    public AiAssistantService(
            DeepSeekChatClient chatClient,
            AiPlatformContextService contextService,
            AiChatMapper chatMapper,
            @Value("${app.ai.provider:DeepSeek}") String provider,
            @Value("${app.ai.model-label:deepseek-chat}") String modelLabel,
            @Value("${app.ai.chat-history-limit:12}") int historyLimit,
            @Value("${app.ai.max-question-length:500}") int maxQuestionLength
    ) {
        this.chatClient = chatClient;
        this.contextService = contextService;
        this.chatMapper = chatMapper;
        this.provider = provider;
        this.modelLabel = modelLabel;
        this.historyLimit = Math.max(2, historyLimit);
        this.maxQuestionLength = Math.max(50, maxQuestionLength);
    }

    public AiStatusView status() {
        boolean configured = chatClient.configured();
        return new AiStatusView(
                provider,
                modelLabel,
                configured,
                configured,
                configured ? SOURCE_DEEPSEEK : SOURCE_LOCAL
        );
    }

    // ------------------------------------------------------------------
    // AI 总结
    // ------------------------------------------------------------------

    @Transactional
    public AiSummaryView summarize(
            String scopeTitle,
            String frameworkId,
            String instruction
    ) {
        AiFramework framework = AiFramework.from(frameworkId);
        String scope = scopeTitle == null || scopeTitle.isBlank()
                ? "全平台业务数据"
                : scopeTitle.trim();
        String extra = trimTo(instruction, MAX_INSTRUCTION_LENGTH);
        AiPlatformContextService.PlatformContext context =
                contextService.collect();

        if (!available()) {
            String notice = unavailabilityNotice();
            return new AiSummaryView(
                    localSummary(scope, framework, extra, context),
                    SOURCE_LOCAL,
                    provider,
                    modelLabel,
                    framework.id(),
                    framework.label(),
                    scope,
                    0L,
                    notice,
                    context.sections(),
                    context.highlights(),
                    Instant.now()
            );
        }

        String systemPrompt = summarySystemPrompt(framework);
        String userPrompt = summaryUserPrompt(scope, extra, context.text());
        long startedAt = System.currentTimeMillis();
        try {
            String content = callModel(systemPrompt, List.of(), userPrompt);
            return new AiSummaryView(
                    content,
                    SOURCE_DEEPSEEK,
                    provider,
                    modelLabel,
                    framework.id(),
                    framework.label(),
                    scope,
                    System.currentTimeMillis() - startedAt,
                    null,
                    context.sections(),
                    context.highlights(),
                    Instant.now()
            );
        } catch (Exception exception) {
            log.warn("AI 总结调用 DeepSeek 失败，回退本地结论", exception);
            return new AiSummaryView(
                    localSummary(scope, framework, extra, context),
                    SOURCE_LOCAL,
                    provider,
                    modelLabel,
                    framework.id(),
                    framework.label(),
                    scope,
                    System.currentTimeMillis() - startedAt,
                    "调用 " + provider + " 失败：" + briefMessage(exception)
                            + "；已改为本地框架结论。",
                    context.sections(),
                    context.highlights(),
                    Instant.now()
            );
        }
    }

    private String summarySystemPrompt(AiFramework framework) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是「寒旱区设施农业数字化管理平台」的农业数据分析助手，")
                .append("服务对象是基地管理员、质检员和监管人员。\n")
                .append("输出要求：\n")
                .append("1. 只能使用下方给出的平台数据，不得编造没有出现的数字、批次、单位或时间。\n")
                .append("2. 用简体中文分点输出，每条 1-2 句，直接给结论和动作，不要客套话和开场白。\n")
                .append("3. 先给整体判断，再给异常项，最后给今天可执行的处置建议。\n")
                .append("4. 数据不足以判断时明确写「当前数据不足以判断」，不要猜。\n")
                .append("5. 涉及链上存证、合格证等可信数据时，说明依据来自哪一项记录。\n")
                .append("6. 不要输出 Markdown 表格，不要输出代码块。\n");
        prompt.append("分析框架：").append(framework.label())
                .append("；关注重点：").append(framework.focus()).append('\n');
        prompt.append("本框架的额外要求：\n");
        for (String line : framework.emphasis()) {
            prompt.append("- ").append(line).append('\n');
        }
        return prompt.toString();
    }

    private String summaryUserPrompt(
            String scope,
            String instruction,
            String contextText
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("分析对象：").append(scope).append('\n');
        prompt.append("补充指令：")
                .append(instruction == null ? "无" : instruction)
                .append('\n');
        prompt.append("平台实时数据：\n").append(contextText).append('\n');
        prompt.append("请输出 5-7 条结论，每条以「数字.」开头，不要输出其他格式。");
        return prompt.toString();
    }

    /**
     * 本地兜底结论：完全基于真实数据拼装，不调用外网。
     * 结论里明确标注来源，避免和模型输出混淆。
     */
    private String localSummary(
            String scope,
            AiFramework framework,
            String instruction,
            AiPlatformContextService.PlatformContext context
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("分析对象：" + scope + "；分析框架：" + framework.label()
                + "（关注" + framework.focus() + "）。");
        if (!context.highlights().isEmpty()) {
            lines.add("当前关键指标：" + String.join("、", context.highlights()) + "。");
        } else {
            lines.add("当前未取到平台指标数据，无法判断运行状态。");
        }
        for (String emphasis : framework.emphasis()) {
            lines.add(emphasis + "。");
        }
        lines.add(instruction == null
                ? "本次未附加额外指令，建议优先处理异常项与未闭环记录。"
                : "本次额外遵循指令：" + instruction + "。");
        lines.add("本条结论由平台本地框架生成，未调用大模型；配置 DEEPSEEK_API_KEY 后重新生成即为模型输出。");

        StringBuilder content = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            content.append(index + 1).append(". ").append(lines.get(index));
            if (index < lines.size() - 1) {
                content.append('\n');
            }
        }
        return content.toString();
    }

    // ------------------------------------------------------------------
    // AI 问答
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AiChatMessageView> history(String sessionId) {
        String session = requireSessionId(sessionId);
        return chatMapper.findAll(session).stream()
                .map(AiChatMessageView::of)
                .toList();
    }

    @Transactional
    public AiChatReplyView chat(
            String sessionId,
            String question,
            String operator
    ) {
        String session = requireSessionId(sessionId);
        String content = requireQuestion(question);
        String operatorName = trimTo(operator, MAX_OPERATOR_LENGTH);

        // 先取历史再落库，避免把本次提问重复拼进上下文
        List<AiChatMapper.ChatRow> recent =
                chatMapper.findRecent(session, historyLimit);
        List<AiChatMapper.ChatRow> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        LocalDateTime askedAt = LocalDateTime.now();
        chatMapper.insert(
                session,
                operatorName,
                ROLE_USER,
                content,
                SOURCE_LOCAL,
                null,
                null,
                askedAt
        );
        AiChatMessageView userMessage = new AiChatMessageView(
                null,
                ROLE_USER,
                content,
                SOURCE_LOCAL,
                null,
                null,
                askedAt
        );

        AiPlatformContextService.PlatformContext context =
                contextService.collect();
        String answer;
        String source;
        String notice;
        long latencyMs = 0L;

        if (!available()) {
            answer = localAnswer(content, context);
            source = SOURCE_LOCAL;
            notice = unavailabilityNotice();
        } else {
            long startedAt = System.currentTimeMillis();
            try {
                answer = callModel(
                        chatSystemPrompt(context.text()),
                        chronological,
                        content
                );
                latencyMs = System.currentTimeMillis() - startedAt;
                source = SOURCE_DEEPSEEK;
                notice = null;
            } catch (Exception exception) {
                log.warn("AI 问答调用 DeepSeek 失败，回退本地回答", exception);
                latencyMs = System.currentTimeMillis() - startedAt;
                answer = localAnswer(content, context);
                source = SOURCE_LOCAL;
                notice = "调用 " + provider + " 失败：" + briefMessage(exception)
                        + "；已改为本地兜底回答。";
            }
        }

        LocalDateTime answeredAt = LocalDateTime.now();
        chatMapper.insert(
                session,
                null,
                ROLE_ASSISTANT,
                answer,
                source,
                source.equals(SOURCE_DEEPSEEK) ? modelLabel : null,
                latencyMs,
                answeredAt
        );
        AiChatMessageView assistantMessage = new AiChatMessageView(
                null,
                ROLE_ASSISTANT,
                answer,
                source,
                source.equals(SOURCE_DEEPSEEK) ? modelLabel : null,
                latencyMs,
                answeredAt
        );
        return new AiChatReplyView(userMessage, assistantMessage, source, notice);
    }

    @Transactional
    public int clear(String sessionId) {
        return chatMapper.deleteBySession(requireSessionId(sessionId));
    }

    private String chatSystemPrompt(String contextText) {
        return """
                你是"寒旱区设施农业数字化管理平台"的 AI 助手，可以回答生产指挥、质量溯源、合作运营、治理监管四类问题。
                回答规则：
                1. 必须基于下方"平台实时数据"回答，数据里没有的内容直接说"平台数据里没有这一项"，不要编造数字。
                2. 用简体中文，先给结论，再给依据，最后给下一步动作（如果用户需要）。
                3. 引用数值时保留原单位，并说明来自哪座温室或哪类记录。
                4. 回答控制在 300 字以内，除非用户明确要求展开。
                5. 涉及链上存证、合格证、检测报告时，要说明依据来自哪一项记录，不要暗示平台数据已经官方法定生效。

                平台实时数据：
                """ + contextText;
    }

    private String localAnswer(
            String question,
            AiPlatformContextService.PlatformContext context
    ) {
        StringBuilder answer = new StringBuilder();
        answer.append("当前未接入大模型，以下是平台本地数据摘要（问题：")
                .append(question)
                .append("）：\n");
        if (context.highlights().isEmpty()) {
            answer.append("- 本次没有取到平台指标数据。\n");
        } else {
            for (String highlight : context.highlights()) {
                answer.append("- ").append(highlight).append('\n');
            }
        }
        answer.append("配置有效的 DEEPSEEK_API_KEY 后重新提问，即可由大模型基于同一批数据分析。");
        return answer.toString();
    }

    // ------------------------------------------------------------------
    // 公共部分
    // ------------------------------------------------------------------

    private String callModel(
            String systemPrompt,
            List<AiChatMapper.ChatRow> history,
            String userContent
    ) {
        List<DeepSeekChatClient.ChatTurn> turns = new ArrayList<>();
        for (AiChatMapper.ChatRow row : history) {
            turns.add(new DeepSeekChatClient.ChatTurn(row.role(), row.content()));
        }
        return chatClient.complete(systemPrompt, turns, userContent).content();
    }

    private boolean available() {
        return chatClient.configured();
    }

    private String unavailabilityNotice() {
        return "未配置 DEEPSEEK_API_KEY，当前展示的是平台本地框架结论。";
    }

    private String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会话标识不能为空");
        }
        String trimmed = sessionId.trim();
        if (trimmed.length() > MAX_SESSION_ID_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "会话标识长度不能超过 " + MAX_SESSION_ID_LENGTH + " 个字符"
            );
        }
        return trimmed;
    }

    private String requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提问内容不能为空");
        }
        String trimmed = question.trim();
        if (trimmed.length() > maxQuestionLength) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "提问内容不能超过 " + maxQuestionLength + " 个字符"
            );
        }
        return trimmed;
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength
                ? trimmed
                : trimmed.substring(0, maxLength);
    }

    private String briefMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 160 ? message : message.substring(0, 160);
    }
}
