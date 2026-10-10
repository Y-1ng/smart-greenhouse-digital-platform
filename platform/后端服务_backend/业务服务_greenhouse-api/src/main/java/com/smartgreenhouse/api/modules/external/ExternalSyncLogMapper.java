package com.smartgreenhouse.api.modules.external;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExternalSyncLogMapper {

    @Insert("""
            INSERT INTO external_sync_log (
              platform, request_no, status, attempts, duration_ms,
              mode, message, payload_summary
            ) VALUES (
              #{platform}, #{requestNo}, #{status}, #{attempts}, #{durationMs},
              #{mode}, #{message}, #{payloadSummary}
            )
            """)
    int insert(ExternalSyncLogInsert log);

    @Select("""
            SELECT
              log_id, platform, request_no, status, attempts, duration_ms,
              mode, message, payload_summary, created_at
            FROM external_sync_log
            ORDER BY log_id DESC
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "log_id", javaType = Long.class),
            @Arg(column = "platform", javaType = String.class),
            @Arg(column = "request_no", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "attempts", javaType = Integer.class),
            @Arg(column = "duration_ms", javaType = Long.class),
            @Arg(column = "mode", javaType = String.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "payload_summary", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<ExternalLogRow> findRecent(@Param("limit") int limit);

    @Select("""
            SELECT
              log_id, platform, request_no, status, attempts, duration_ms,
              mode, message, payload_summary, created_at
            FROM external_sync_log
            WHERE platform = #{platform}
            ORDER BY log_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "log_id", javaType = Long.class),
            @Arg(column = "platform", javaType = String.class),
            @Arg(column = "request_no", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "attempts", javaType = Integer.class),
            @Arg(column = "duration_ms", javaType = Long.class),
            @Arg(column = "mode", javaType = String.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "payload_summary", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    ExternalLogRow findLatest(@Param("platform") String platform);

    @Select("""
            SELECT COUNT(*) FROM external_sync_log
            WHERE platform = #{platform} AND status = #{status}
            """)
    int countByStatus(
            @Param("platform") String platform,
            @Param("status") String status
    );

    /**
     * 同步目标：气象与产销平台按温室逐个调用。
     */
    @Select("""
            SELECT greenhouse_code, location
            FROM greenhouse
            ORDER BY greenhouse_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "location", javaType = String.class)
    })
    List<GreenhouseTarget> findGreenhouseTargets(@Param("limit") int limit);

    record ExternalSyncLogInsert(
            String platform,
            String requestNo,
            String status,
            int attempts,
            long durationMs,
            String mode,
            String message,
            String payloadSummary
    ) {
    }

    record ExternalLogRow(
            Long logId,
            String platform,
            String requestNo,
            String status,
            Integer attempts,
            Long durationMs,
            String mode,
            String message,
            String payloadSummary,
            LocalDateTime createdAt
    ) {
    }

    record GreenhouseTarget(String greenhouseCode, String location) {
    }

    @Select("""
            SELECT greenhouse_code FROM greenhouse
            WHERE greenhouse_id = #{greenhouseId}
            """)
    String findGreenhouseCode(@Param("greenhouseId") Long greenhouseId);
}
