package com.smartgreenhouse.api.modules.environment;

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
public interface EnvironmentThresholdMapper {

    @Select("""
            SELECT greenhouse_id
            FROM greenhouse
            WHERE greenhouse_code = #{greenhouseCode}
            """)
    Long findGreenhouseId(@Param("greenhouseCode") String greenhouseCode);

    @Select("""
            SELECT
              threshold_id,
              metric_code,
              target_min,
              target_max,
              alert_min,
              alert_max,
              control_action,
              enabled,
              updated_at
            FROM environment_threshold
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY threshold_id
            """)
    @ConstructorArgs({
            @Arg(column = "threshold_id", javaType = Long.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "target_min", javaType = BigDecimal.class),
            @Arg(column = "target_max", javaType = BigDecimal.class),
            @Arg(column = "alert_min", javaType = BigDecimal.class),
            @Arg(column = "alert_max", javaType = BigDecimal.class),
            @Arg(column = "control_action", javaType = String.class),
            @Arg(column = "enabled", javaType = Boolean.class),
            @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    List<ThresholdRow> findByGreenhouseId(
            @Param("greenhouseId") Long greenhouseId
    );

    @Select("""
            SELECT
              threshold_id,
              metric_code,
              target_min,
              target_max,
              alert_min,
              alert_max,
              control_action,
              enabled,
              updated_at
            FROM environment_threshold
            WHERE greenhouse_id = #{greenhouseId}
              AND metric_code = #{metricCode}
            """)
    @ConstructorArgs({
            @Arg(column = "threshold_id", javaType = Long.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "target_min", javaType = BigDecimal.class),
            @Arg(column = "target_max", javaType = BigDecimal.class),
            @Arg(column = "alert_min", javaType = BigDecimal.class),
            @Arg(column = "alert_max", javaType = BigDecimal.class),
            @Arg(column = "control_action", javaType = String.class),
            @Arg(column = "enabled", javaType = Boolean.class),
            @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    ThresholdRow findOne(
            @Param("greenhouseId") Long greenhouseId,
            @Param("metricCode") String metricCode
    );

    @Update("""
            UPDATE environment_threshold
            SET
              target_min = #{targetMin},
              target_max = #{targetMax},
              alert_min = #{alertMin},
              alert_max = #{alertMax},
              control_action = #{controlAction},
              enabled = #{enabled},
              updated_at = CURRENT_TIMESTAMP
            WHERE greenhouse_id = #{greenhouseId}
              AND metric_code = #{metricCode}
            """)
    int update(ThresholdUpdate threshold);

    @Insert("""
            INSERT INTO environment_threshold (
              greenhouse_id,
              metric_code,
              target_min,
              target_max,
              alert_min,
              alert_max,
              control_action,
              enabled
            ) VALUES (
              #{greenhouseId},
              #{metricCode},
              #{targetMin},
              #{targetMax},
              #{alertMin},
              #{alertMax},
              #{controlAction},
              #{enabled}
            )
            """)
    int insert(ThresholdUpdate threshold);

    record ThresholdRow(
            Long thresholdId,
            String metricCode,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal alertMin,
            BigDecimal alertMax,
            String controlAction,
            Boolean enabled,
            LocalDateTime updatedAt
    ) {
    }

    record ThresholdUpdate(
            Long greenhouseId,
            String metricCode,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal alertMin,
            BigDecimal alertMax,
            String controlAction,
            Boolean enabled
    ) {
    }
}
