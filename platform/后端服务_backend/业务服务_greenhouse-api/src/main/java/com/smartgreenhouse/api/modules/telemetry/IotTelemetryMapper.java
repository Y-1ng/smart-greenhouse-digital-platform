package com.smartgreenhouse.api.modules.telemetry;

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
public interface IotTelemetryMapper {

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
            SELECT greenhouse_code
            FROM greenhouse
            ORDER BY greenhouse_id
            """)
    List<String> findAllGreenhouseCodes();

    @Select("""
            SELECT device_id
            FROM device
            WHERE greenhouse_id = #{greenhouseId}
              AND device_code = #{deviceCode}
            """)
    Long findDeviceId(
            @Param("greenhouseId") Long greenhouseId,
            @Param("deviceCode") String deviceCode
    );

    @Select("""
            SELECT device_code
            FROM device
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY device_id
            LIMIT 1
            """)
    String findFirstDeviceCode(@Param("greenhouseId") Long greenhouseId);

    @Insert("""
            INSERT INTO iot_telemetry (
              greenhouse_id,
              device_id,
              metric_code,
              metric_value,
              unit,
              collected_at,
              raw_topic
            ) VALUES (
              #{greenhouseId},
              #{deviceId},
              #{metricCode},
              #{metricValue},
              #{unit},
              #{collectedAt},
              #{rawTopic}
            )
            """)
    int insert(TelemetryInsert telemetry);

    /**
     * 设备状态上报：更新在线状态与最后在线时间。
     */
    @Update("""
            UPDATE device
            SET online_status = #{onlineStatus},
                last_seen_at = #{lastSeenAt}
            WHERE greenhouse_id = #{greenhouseId}
              AND device_code = #{deviceCode}
            """)
    int updateDeviceStatus(
            @Param("greenhouseId") Long greenhouseId,
            @Param("deviceCode") String deviceCode,
            @Param("onlineStatus") String onlineStatus,
            @Param("lastSeenAt") LocalDateTime lastSeenAt
    );

    /**
     * 遥测到达即视为设备在线，顺带刷新最后在线时间。
     */
    @Update("""
            UPDATE device
            SET online_status = 'ONLINE',
                last_seen_at = #{lastSeenAt}
            WHERE greenhouse_id = #{greenhouseId}
              AND device_code = #{deviceCode}
            """)
    int touchDevice(
            @Param("greenhouseId") Long greenhouseId,
            @Param("deviceCode") String deviceCode,
            @Param("lastSeenAt") LocalDateTime lastSeenAt
    );

    @Select("""
            <script>
            SELECT
              telemetry_id,
              greenhouse_code,
              device_code,
              metric_code,
              metric_value,
              unit,
              collected_at,
              raw_topic
            FROM (
              SELECT
                t.telemetry_id,
                g.greenhouse_code,
                d.device_code,
                t.metric_code,
                t.metric_value,
                t.unit,
                t.collected_at,
                t.raw_topic,
                ROW_NUMBER() OVER (
                  PARTITION BY t.metric_code
                  ORDER BY t.collected_at DESC, t.telemetry_id DESC
                ) AS row_no
              FROM iot_telemetry t
              JOIN greenhouse g ON g.greenhouse_id = t.greenhouse_id
              JOIN device d ON d.device_id = t.device_id
              WHERE g.greenhouse_code = #{greenhouseCode}
            ) ranked
            WHERE row_no = 1
            <if test="metricCode != null and metricCode != ''">
              AND metric_code = #{metricCode}
            </if>
            ORDER BY metric_code
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "telemetry_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "collected_at", javaType = LocalDateTime.class),
            @Arg(column = "raw_topic", javaType = String.class)
    })
    List<TelemetryRow> findLatest(
            @Param("greenhouseCode") String greenhouseCode,
            @Param("metricCode") String metricCode
    );

    @Select("""
            SELECT
              t.telemetry_id,
              g.greenhouse_code,
              d.device_code,
              t.metric_code,
              t.metric_value,
              t.unit,
              t.collected_at,
              t.raw_topic
            FROM iot_telemetry t
            JOIN greenhouse g ON g.greenhouse_id = t.greenhouse_id
            JOIN device d ON d.device_id = t.device_id
            WHERE g.greenhouse_code = #{greenhouseCode}
              AND t.collected_at > #{after}
              AND t.collected_at <= #{until}
            ORDER BY t.collected_at, t.telemetry_id
            """)
    @ConstructorArgs({
            @Arg(column = "telemetry_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "collected_at", javaType = LocalDateTime.class),
            @Arg(column = "raw_topic", javaType = String.class)
    })
    List<TelemetryRow> findReadingsBetween(
            @Param("greenhouseCode") String greenhouseCode,
            @Param("after") LocalDateTime after,
            @Param("until") LocalDateTime until
    );

    record GreenhouseRow(Long greenhouseId, String greenhouseCode) {
    }

    record TelemetryInsert(
            Long greenhouseId,
            Long deviceId,
            String metricCode,
            BigDecimal metricValue,
            String unit,
            LocalDateTime collectedAt,
            String rawTopic
    ) {
    }

    record TelemetryRow(
            Long telemetryId,
            String greenhouseCode,
            String deviceCode,
            String metricCode,
            BigDecimal metricValue,
            String unit,
            LocalDateTime collectedAt,
            String rawTopic
    ) {
    }
}
