package com.smartgreenhouse.api.modules.ai;

import java.time.LocalDateTime;

/**
 * 一条问答消息。role=user 是提问，role=assistant 是模型回答。
 */
public record AiChatMessageView(
        Long messageId,
        String role,
        String content,
        String source,
        String model,
        Long latencyMs,
        LocalDateTime createdAt
) {

    public static AiChatMessageView of(AiChatMapper.ChatRow row) {
        return new AiChatMessageView(
                row.messageId(),
                row.role(),
                row.content(),
                row.source(),
                row.model(),
                row.latencyMs(),
                row.createdAt()
        );
    }
}
