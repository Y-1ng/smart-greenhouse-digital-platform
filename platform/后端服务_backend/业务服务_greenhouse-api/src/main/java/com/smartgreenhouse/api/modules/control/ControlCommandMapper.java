package com.smartgreenhouse.api.modules.control;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ControlCommandMapper {

    @Select("""
            SELECT greenhouse_id
            FROM greenhouse
            WHERE greenhouse_code = #{greenhouseCode}
            """)
    Long findGreenhouseId(@Param("greenhouseCode") String greenhouseCode);

    @Select("""
            SELECT device_id
            FROM device
            WHERE device_code = #{deviceCode}
              AND greenhouse_id = #{greenhouseId}
            """)
    Long findDeviceId(
            @Param("greenhouseId") Long greenhouseId,
            @Param("deviceCode") String deviceCode
    );

    @Insert("""
            INSERT INTO control_command (
              command_no,
              greenhouse_id,
              device_id,
              command_type,
              command_payload,
              operator_name,
              timeout_seconds,
              status
            ) VALUES (
              #{commandNo},
              #{greenhouseId},
              #{deviceId},
              #{commandType},
              #{commandPayload},
              #{operatorName},
              #{timeoutSeconds},
              'CREATED'
            )
            """)
    int insert(ControlCommandInsert command);

    @Select("""
            SELECT
              c.command_no,
              c.device_id,
              g.greenhouse_code,
              d.device_code,
              c.command_type,
              CAST(c.command_payload AS VARCHAR(4000)) AS command_payload,
              c.operator_name,
              c.timeout_seconds,
              c.status,
              c.failure_reason,
              c.created_at,
              c.acknowledged_at,
              c.completed_at,
              c.dispatched_at,
              COALESCE(c.attempt_started_at, c.created_at) AS attempt_started_at,
              c.retry_count
            FROM control_command c
            JOIN greenhouse g ON g.greenhouse_id = c.greenhouse_id
            JOIN device d ON d.device_id = c.device_id
            WHERE c.command_no = #{commandNo}
            """)
    @ConstructorArgs({
            @Arg(column = "command_no", javaType = String.class),
            @Arg(column = "device_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "command_type", javaType = String.class),
            @Arg(column = "command_payload", javaType = String.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "timeout_seconds", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "failure_reason", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_at", javaType = LocalDateTime.class),
            @Arg(column = "completed_at", javaType = LocalDateTime.class),
            @Arg(column = "dispatched_at", javaType = LocalDateTime.class),
            @Arg(column = "attempt_started_at", javaType = LocalDateTime.class),
            @Arg(column = "retry_count", javaType = java.lang.Integer.class)
    })
    CommandRow findByCommandNo(@Param("commandNo") String commandNo);

    @Select("""
            SELECT
              c.command_no,
              c.device_id,
              g.greenhouse_code,
              d.device_code,
              c.command_type,
              CAST(c.command_payload AS VARCHAR(4000)) AS command_payload,
              c.operator_name,
              c.timeout_seconds,
              c.status,
              c.failure_reason,
              c.created_at,
              c.acknowledged_at,
              c.completed_at,
              c.dispatched_at,
              COALESCE(c.attempt_started_at, c.created_at) AS attempt_started_at,
              c.retry_count
            FROM control_command c
            JOIN greenhouse g ON g.greenhouse_id = c.greenhouse_id
            JOIN device d ON d.device_id = c.device_id
            WHERE c.status = 'CREATED'
            ORDER BY c.created_at, c.command_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "command_no", javaType = String.class),
            @Arg(column = "device_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "command_type", javaType = String.class),
            @Arg(column = "command_payload", javaType = String.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "timeout_seconds", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "failure_reason", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_at", javaType = LocalDateTime.class),
            @Arg(column = "completed_at", javaType = LocalDateTime.class),
            @Arg(column = "dispatched_at", javaType = LocalDateTime.class),
            @Arg(column = "attempt_started_at", javaType = LocalDateTime.class),
            @Arg(column = "retry_count", javaType = java.lang.Integer.class)
    })
    List<CommandRow> findPendingDispatch(@Param("limit") int limit);

    @Select("""
            SELECT
              c.command_no,
              c.device_id,
              g.greenhouse_code,
              d.device_code,
              c.command_type,
              CAST(c.command_payload AS VARCHAR(4000)) AS command_payload,
              c.operator_name,
              c.timeout_seconds,
              c.status,
              c.failure_reason,
              c.created_at,
              c.acknowledged_at,
              c.completed_at,
              c.dispatched_at,
              COALESCE(c.attempt_started_at, c.created_at) AS attempt_started_at,
              c.retry_count
            FROM control_command c
            JOIN greenhouse g ON g.greenhouse_id = c.greenhouse_id
            JOIN device d ON d.device_id = c.device_id
            WHERE c.status = 'DISPATCHED'
            ORDER BY c.dispatched_at, c.command_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "command_no", javaType = String.class),
            @Arg(column = "device_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "command_type", javaType = String.class),
            @Arg(column = "command_payload", javaType = String.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "timeout_seconds", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "failure_reason", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_at", javaType = LocalDateTime.class),
            @Arg(column = "completed_at", javaType = LocalDateTime.class),
            @Arg(column = "dispatched_at", javaType = LocalDateTime.class),
            @Arg(column = "attempt_started_at", javaType = LocalDateTime.class),
            @Arg(column = "retry_count", javaType = java.lang.Integer.class)
    })
    List<CommandRow> findDispatched(@Param("limit") int limit);

    /**
     * 未结束的指令（已创建或已下发），超时判定在服务层按 attempt_started_at 计算，
     * 避免依赖各数据库的日期加减方言。
     */
    @Select("""
            SELECT
              c.command_no,
              c.device_id,
              g.greenhouse_code,
              d.device_code,
              c.command_type,
              CAST(c.command_payload AS VARCHAR(4000)) AS command_payload,
              c.operator_name,
              c.timeout_seconds,
              c.status,
              c.failure_reason,
              c.created_at,
              c.acknowledged_at,
              c.completed_at,
              c.dispatched_at,
              COALESCE(c.attempt_started_at, c.created_at) AS attempt_started_at,
              c.retry_count
            FROM control_command c
            JOIN greenhouse g ON g.greenhouse_id = c.greenhouse_id
            JOIN device d ON d.device_id = c.device_id
            WHERE c.status IN ('CREATED', 'DISPATCHED')
            ORDER BY c.created_at, c.command_id
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "command_no", javaType = String.class),
            @Arg(column = "device_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "command_type", javaType = String.class),
            @Arg(column = "command_payload", javaType = String.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "timeout_seconds", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "failure_reason", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_at", javaType = LocalDateTime.class),
            @Arg(column = "completed_at", javaType = LocalDateTime.class),
            @Arg(column = "dispatched_at", javaType = LocalDateTime.class),
            @Arg(column = "attempt_started_at", javaType = LocalDateTime.class),
            @Arg(column = "retry_count", javaType = java.lang.Integer.class)
    })
    List<CommandRow> findOpenCommands(@Param("limit") int limit);

    @Update("""
            UPDATE control_command
            SET status = 'DISPATCHED',
                dispatched_at = #{dispatchedAt}
            WHERE command_no = #{commandNo}
              AND status = 'CREATED'
            """)
    int markDispatched(
            @Param("commandNo") String commandNo,
            @Param("dispatchedAt") LocalDateTime dispatchedAt
    );

    /**
     * 超时重试：状态回到 CREATED 等待重新下发，本轮计时从 now 重新开始。
     */
    @Update("""
            UPDATE control_command
            SET status = 'CREATED',
                retry_count = retry_count + 1,
                attempt_started_at = #{attemptStartedAt},
                dispatched_at = NULL,
                failure_reason = #{failureReason}
            WHERE command_no = #{commandNo}
            """)
    int retry(
            @Param("commandNo") String commandNo,
            @Param("attemptStartedAt") LocalDateTime attemptStartedAt,
            @Param("failureReason") String failureReason
    );

    @Update("""
            UPDATE control_command
            SET status = 'FAILED',
                failure_reason = #{failureReason},
                completed_at = #{completedAt}
            WHERE command_no = #{commandNo}
            """)
    int fail(
            @Param("commandNo") String commandNo,
            @Param("failureReason") String failureReason,
            @Param("completedAt") LocalDateTime completedAt
    );

    @Update("""
            UPDATE control_command
            SET
              status = #{status},
              failure_reason = #{failureReason},
              acknowledged_at = #{acknowledgedAt},
              completed_at = #{completedAt}
            WHERE command_no = #{commandNo}
            """)
    int acknowledge(AcknowledgementUpdate acknowledgement);

    @Update("""
            UPDATE device
            SET operating_state = #{operatingState}
            WHERE device_id = #{deviceId}
            """)
    int updateDeviceOperatingState(
            @Param("deviceId") Long deviceId,
            @Param("operatingState") String operatingState
    );

    record ControlCommandInsert(
            String commandNo,
            Long greenhouseId,
            Long deviceId,
            String commandType,
            String commandPayload,
            String operatorName,
            int timeoutSeconds
    ) {
    }

    record CommandRow(
            String commandNo,
            Long deviceId,
            String greenhouseCode,
            String deviceCode,
            String commandType,
            String commandPayload,
            String operatorName,
            int timeoutSeconds,
            String status,
            String failureReason,
            LocalDateTime createdAt,
            LocalDateTime acknowledgedAt,
            LocalDateTime completedAt,
            LocalDateTime dispatchedAt,
            LocalDateTime attemptStartedAt,
            Integer retryCount
    ) {
    }

    record AcknowledgementUpdate(
            String commandNo,
            String status,
            String failureReason,
            LocalDateTime acknowledgedAt,
            LocalDateTime completedAt
    ) {
    }
}
