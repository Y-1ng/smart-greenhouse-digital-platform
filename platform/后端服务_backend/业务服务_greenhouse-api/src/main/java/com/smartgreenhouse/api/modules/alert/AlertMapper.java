package com.smartgreenhouse.api.modules.alert;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertMapper {

    @Select("""
            SELECT greenhouse_id, greenhouse_code
            FROM greenhouse
            WHERE greenhouse_code = #{greenhouseCode}
            """)
    @ConstructorArgs({
            @Arg(column = "greenhouse_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class)
    })
    GreenhouseRow findGreenhouseByCode(
            @Param("greenhouseCode") String greenhouseCode
    );

    @Select("""
            SELECT COUNT(*)
            FROM alert_record
            WHERE greenhouse_id = #{greenhouseId}
              AND metric_code = #{metricCode}
              AND status = 'PENDING'
            """)
    int countPending(
            @Param("greenhouseId") Long greenhouseId,
            @Param("metricCode") String metricCode
    );

    @Insert("""
            INSERT INTO alert_record (
              greenhouse_id,
              metric_code,
              metric_value,
              alert_level,
              alert_min,
              alert_max,
              message,
              status,
              created_at
            ) VALUES (
              #{greenhouseId},
              #{metricCode},
              #{metricValue},
              #{alertLevel},
              #{alertMin},
              #{alertMax},
              #{message},
              'PENDING',
              #{createdAt}
            )
            """)
    int insert(AlertInsert alert);

    @Select("""
            <script>
            SELECT
              a.alert_id,
              g.greenhouse_code,
              a.metric_code,
              a.metric_value,
              a.alert_level,
              a.alert_min,
              a.alert_max,
              a.message,
              a.status,
              a.created_at,
              a.acknowledged_at,
              a.acknowledged_by
            FROM alert_record a
            JOIN greenhouse g ON g.greenhouse_id = a.greenhouse_id
            WHERE 1 = 1
            <if test="greenhouseCode != null">
              AND g.greenhouse_code = #{greenhouseCode}
            </if>
            <if test="status != null">
              AND a.status = #{status}
            </if>
            ORDER BY a.created_at DESC, a.alert_id DESC
            LIMIT #{limit}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "alert_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "alert_level", javaType = String.class),
            @Arg(column = "alert_min", javaType = BigDecimal.class),
            @Arg(column = "alert_max", javaType = BigDecimal.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_by", javaType = String.class)
    })
    List<AlertRow> findAlerts(
            @Param("greenhouseCode") String greenhouseCode,
            @Param("status") String status,
            @Param("limit") int limit
    );

    @Select("""
            SELECT
              a.alert_id,
              g.greenhouse_code,
              a.metric_code,
              a.metric_value,
              a.alert_level,
              a.alert_min,
              a.alert_max,
              a.message,
              a.status,
              a.created_at,
              a.acknowledged_at,
              a.acknowledged_by
            FROM alert_record a
            JOIN greenhouse g ON g.greenhouse_id = a.greenhouse_id
            WHERE a.alert_id = #{alertId}
            """)
    @ConstructorArgs({
            @Arg(column = "alert_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "alert_level", javaType = String.class),
            @Arg(column = "alert_min", javaType = BigDecimal.class),
            @Arg(column = "alert_max", javaType = BigDecimal.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_at", javaType = LocalDateTime.class),
            @Arg(column = "acknowledged_by", javaType = String.class)
    })
    AlertRow findOne(@Param("alertId") Long alertId);

    @Update("""
            UPDATE alert_record
            SET
              status = 'ACKNOWLEDGED',
              acknowledged_at = #{acknowledgedAt},
              acknowledged_by = #{acknowledgedBy}
            WHERE alert_id = #{alertId}
              AND status = 'PENDING'
            """)
    int acknowledge(
            @Param("alertId") Long alertId,
            @Param("acknowledgedBy") String acknowledgedBy,
            @Param("acknowledgedAt") LocalDateTime acknowledgedAt
    );

    record GreenhouseRow(Long greenhouseId, String greenhouseCode) {
    }

    record AlertInsert(
            Long greenhouseId,
            String metricCode,
            BigDecimal metricValue,
            String alertLevel,
            BigDecimal alertMin,
            BigDecimal alertMax,
            String message,
            LocalDateTime createdAt
    ) {
    }

    record AlertRow(
            Long alertId,
            String greenhouseCode,
            String metricCode,
            BigDecimal metricValue,
            String alertLevel,
            BigDecimal alertMin,
            BigDecimal alertMax,
            String message,
            String status,
            LocalDateTime createdAt,
            LocalDateTime acknowledgedAt,
            String acknowledgedBy
    ) {
    }
}
