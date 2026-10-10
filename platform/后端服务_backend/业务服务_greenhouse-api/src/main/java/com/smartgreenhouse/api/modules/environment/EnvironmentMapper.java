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
public interface EnvironmentMapper {

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
            SELECT
              t.device_id,
              d.device_code,
              t.metric_code,
              t.metric_value,
              t.unit,
              t.collected_at
            FROM iot_telemetry t
            JOIN device d ON d.device_id = t.device_id
            WHERE t.greenhouse_id = #{greenhouseId}
              AND t.collected_at > #{after}
              AND t.collected_at <= #{until}
            ORDER BY t.collected_at, t.telemetry_id
            """)
    @ConstructorArgs({
            @Arg(column = "device_id", javaType = Long.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "collected_at", javaType = LocalDateTime.class)
    })
    List<TelemetryReadingRow> findTelemetryReadings(
            @Param("greenhouseId") Long greenhouseId,
            @Param("after") LocalDateTime after,
            @Param("until") LocalDateTime until
    );

    @Select("""
            SELECT
              cycle_id,
              cycle_start,
              cycle_end,
              root_hash,
              chain_status
            FROM environment_cycle
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY cycle_end DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "cycle_id", javaType = Long.class),
            @Arg(column = "cycle_start", javaType = LocalDateTime.class),
            @Arg(column = "cycle_end", javaType = LocalDateTime.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "chain_status", javaType = String.class)
    })
    CycleRow findLatestCycle(@Param("greenhouseId") Long greenhouseId);

    @Select("""
            SELECT
              cycle_id,
              cycle_start,
              cycle_end,
              root_hash,
              chain_status
            FROM environment_cycle
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY cycle_end DESC
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "cycle_id", javaType = Long.class),
            @Arg(column = "cycle_start", javaType = LocalDateTime.class),
            @Arg(column = "cycle_end", javaType = LocalDateTime.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "chain_status", javaType = String.class)
    })
    List<CycleRow> findRecentCycles(
            @Param("greenhouseId") Long greenhouseId,
            @Param("limit") int limit
    );

    @Select("""
            SELECT metric_code, metric_value, unit, collected_at
            FROM sensor_reading
            WHERE cycle_id = #{cycleId}
            ORDER BY reading_id
            """)
    @ConstructorArgs({
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class),
            @Arg(column = "unit", javaType = String.class),
            @Arg(column = "collected_at", javaType = LocalDateTime.class)
    })
    List<ReadingRow> findReadings(@Param("cycleId") Long cycleId);

    @Select("""
            SELECT cycle_id, cycle_start, cycle_end, root_hash, chain_status
            FROM environment_cycle
            WHERE greenhouse_id = #{greenhouseId}
              AND cycle_start = #{cycleStart}
              AND cycle_end = #{cycleEnd}
            """)
    @ConstructorArgs({
            @Arg(column = "cycle_id", javaType = Long.class),
            @Arg(column = "cycle_start", javaType = LocalDateTime.class),
            @Arg(column = "cycle_end", javaType = LocalDateTime.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "chain_status", javaType = String.class)
    })
    CycleRow findCycleByWindow(
            @Param("greenhouseId") Long greenhouseId,
            @Param("cycleStart") LocalDateTime cycleStart,
            @Param("cycleEnd") LocalDateTime cycleEnd
    );

    @Insert("""
            INSERT INTO environment_cycle (
              greenhouse_id,
              cycle_start,
              cycle_end,
              data_digest,
              root_hash,
              chain_status
            ) VALUES (
              #{greenhouseId},
              #{cycleStart},
              #{cycleEnd},
              #{dataDigest},
              #{rootHash},
              'PENDING'
            )
            """)
    int insertCycle(CycleInsert cycle);

    @Update("""
            UPDATE environment_cycle
            SET chain_status = #{chainStatus},
                chain_tx_hash = #{chainTxHash}
            WHERE cycle_id = #{cycleId}
            """)
    int updateChainReceipt(
            @Param("cycleId") Long cycleId,
            @Param("chainTxHash") String chainTxHash,
            @Param("chainStatus") String chainStatus
    );

    @Insert("""
            INSERT INTO sensor_reading (
              cycle_id,
              device_id,
              metric_code,
              metric_value,
              unit,
              collected_at
            ) VALUES (
              #{cycleId},
              #{deviceId},
              #{metricCode},
              #{metricValue},
              #{unit},
              #{collectedAt}
            )
            """)
    int insertReading(ReadingInsert reading);

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

    record GreenhouseRow(Long greenhouseId, String greenhouseCode) {
    }

    record TelemetryReadingRow(
            Long deviceId,
            String deviceCode,
            String metricCode,
            BigDecimal metricValue,
            String unit,
            LocalDateTime collectedAt
    ) {
    }

    record CycleRow(
            Long cycleId,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd,
            String rootHash,
            String chainStatus
    ) {
    }

    record ReadingRow(
            String metricCode,
            BigDecimal metricValue,
            String unit,
            LocalDateTime collectedAt
    ) {
    }

    record CycleInsert(
            Long greenhouseId,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd,
            String dataDigest,
            String rootHash
    ) {
    }

    record ReadingInsert(
            Long cycleId,
            Long deviceId,
            String metricCode,
            BigDecimal metricValue,
            String unit,
            LocalDateTime collectedAt
    ) {
    }
}
