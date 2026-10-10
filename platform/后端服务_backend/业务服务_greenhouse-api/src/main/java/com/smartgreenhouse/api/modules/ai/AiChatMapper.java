package com.smartgreenhouse.api.modules.ai;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 问答会话记录读写。按 session_id 分组，前端换一个会话地址就是一段新对话。
 */
@Mapper
public interface AiChatMapper {

    @Insert("""
            INSERT INTO ai_chat_message (
              session_id,
              operator_name,
              role,
              content,
              source,
              model,
              latency_ms,
              created_at
            ) VALUES (
              #{sessionId},
              #{operatorName},
              #{role},
              #{content},
              #{source},
              #{model},
              #{latencyMs},
              #{createdAt}
            )
            """)
    int insert(
            @Param("sessionId") String sessionId,
            @Param("operatorName") String operatorName,
            @Param("role") String role,
            @Param("content") String content,
            @Param("source") String source,
            @Param("model") String model,
            @Param("latencyMs") Long latencyMs,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Select("""
            SELECT
              message_id,
              session_id,
              role,
              content,
              source,
              model,
              latency_ms,
              created_at
            FROM ai_chat_message
            WHERE session_id = #{sessionId}
            ORDER BY message_id ASC
            """)
    @ConstructorArgs({
            @Arg(column = "message_id", javaType = Long.class),
            @Arg(column = "session_id", javaType = String.class),
            @Arg(column = "role", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "source", javaType = String.class),
            @Arg(column = "model", javaType = String.class),
            @Arg(column = "latency_ms", javaType = Long.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<ChatRow> findAll(@Param("sessionId") String sessionId);

    @Select("""
            SELECT
              message_id,
              session_id,
              role,
              content,
              source,
              model,
              latency_ms,
              created_at
            FROM ai_chat_message
            WHERE session_id = #{sessionId}
            ORDER BY message_id DESC
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "message_id", javaType = Long.class),
            @Arg(column = "session_id", javaType = String.class),
            @Arg(column = "role", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "source", javaType = String.class),
            @Arg(column = "model", javaType = String.class),
            @Arg(column = "latency_ms", javaType = Long.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<ChatRow> findRecent(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    @Delete("""
            DELETE FROM ai_chat_message
            WHERE session_id = #{sessionId}
            """)
    int deleteBySession(@Param("sessionId") String sessionId);

    record ChatRow(
            Long messageId,
            String sessionId,
            String role,
            String content,
            String source,
            String model,
            Long latencyMs,
            LocalDateTime createdAt
    ) {
    }
}
