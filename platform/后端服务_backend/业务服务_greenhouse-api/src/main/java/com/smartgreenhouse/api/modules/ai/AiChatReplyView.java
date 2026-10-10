package com.smartgreenhouse.api.modules.ai;

/**
 * 一次问答的结果：这一轮的提问 + 回答。
 *
 * @param source deepseek=大模型回答；local=本地兜底
 * @param notice 降级原因，正常回答时为 null
 */
public record AiChatReplyView(
        AiChatMessageView userMessage,
        AiChatMessageView assistantMessage,
        String source,
        String notice
) {
}
