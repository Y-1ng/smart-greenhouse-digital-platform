package com.smartgreenhouse.api.modules.greenhouse;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GreenhouseOverviewMapper {

    @Select("""
            SELECT greenhouse_id, greenhouse_code, greenhouse_name, area, location, status
            FROM greenhouse
            WHERE greenhouse_code = #{code}
            """)
    @ConstructorArgs({
            @Arg(column = "greenhouse_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "greenhouse_name", javaType = String.class),
            @Arg(column = "area", javaType = BigDecimal.class),
            @Arg(column = "location", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    GreenhouseRow findGreenhouseByCode(@Param("code") String code);

    @Select("""
            SELECT greenhouse_id, greenhouse_code, greenhouse_name, area, location, status
            FROM greenhouse
            ORDER BY greenhouse_id
            """)
    @ConstructorArgs({
            @Arg(column = "greenhouse_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "greenhouse_name", javaType = String.class),
            @Arg(column = "area", javaType = BigDecimal.class),
            @Arg(column = "location", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    List<GreenhouseRow> findAllGreenhouses();

    @Select("""
            SELECT
              g.greenhouse_id,
              g.greenhouse_code,
              g.greenhouse_name,
              g.area,
              g.location,
              g.status,
              cb.batch_id,
              cb.crop_name,
              cb.variety_name,
              cb.planting_date,
              cb.expected_harvest_date
            FROM greenhouse g
            LEFT JOIN crop_batch cb ON cb.batch_id = (
              SELECT latest.batch_id
              FROM crop_batch latest
              WHERE latest.greenhouse_id = g.greenhouse_id
              ORDER BY latest.planting_date DESC, latest.batch_id DESC
              LIMIT 1
            )
            ORDER BY g.greenhouse_id
            """)
    @ConstructorArgs({
            @Arg(column = "greenhouse_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "greenhouse_name", javaType = String.class),
            @Arg(column = "area", javaType = BigDecimal.class),
            @Arg(column = "location", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class),
            @Arg(column = "variety_name", javaType = String.class),
            @Arg(column = "planting_date", javaType = LocalDate.class),
            @Arg(column = "expected_harvest_date", javaType = LocalDate.class)
    })
    List<GreenhouseSummary> findAllSummaries();

    @Select("""
            SELECT cycle_id, cycle_start, cycle_end, data_digest, root_hash, chain_status
            FROM environment_cycle
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY cycle_end DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "cycle_id", javaType = Long.class),
            @Arg(column = "cycle_start", javaType = LocalDateTime.class),
            @Arg(column = "cycle_end", javaType = LocalDateTime.class),
            @Arg(column = "data_digest", javaType = String.class),
            @Arg(column = "root_hash", javaType = String.class),
            @Arg(column = "chain_status", javaType = String.class)
    })
    CycleRow findLatestCycle(@Param("greenhouseId") Long greenhouseId);

    @Select("""
            SELECT metric_code, metric_value
            FROM sensor_reading
            WHERE cycle_id = #{cycleId}
            ORDER BY reading_id
            """)
    @ConstructorArgs({
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class)
    })
    List<MetricRow> findMetrics(@Param("cycleId") Long cycleId);

    @Select("""
            SELECT t.metric_code, t.metric_value
            FROM iot_telemetry t
            WHERE t.greenhouse_id = #{greenhouseId}
              AND t.collected_at = (
                SELECT MAX(inner_telemetry.collected_at)
                FROM iot_telemetry inner_telemetry
                WHERE inner_telemetry.greenhouse_id = t.greenhouse_id
                  AND inner_telemetry.metric_code = t.metric_code
              )
            ORDER BY t.telemetry_id
            """)
    @ConstructorArgs({
            @Arg(column = "metric_code", javaType = String.class),
            @Arg(column = "metric_value", javaType = BigDecimal.class)
    })
    List<MetricRow> findLatestTelemetryMetrics(
            @Param("greenhouseId") Long greenhouseId
    );

    @Select("""
            SELECT
              device_id,
              device_code,
              device_type,
              online_status,
              operating_state,
              last_seen_at
            FROM device
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY device_id
            """)
    @ConstructorArgs({
            @Arg(column = "device_id", javaType = Long.class),
            @Arg(column = "device_code", javaType = String.class),
            @Arg(column = "device_type", javaType = String.class),
            @Arg(column = "online_status", javaType = String.class),
            @Arg(column = "operating_state", javaType = String.class),
            @Arg(column = "last_seen_at", javaType = LocalDateTime.class)
    })
    List<DeviceRow> findDevices(@Param("greenhouseId") Long greenhouseId);

    @Select("""
            SELECT batch_id, crop_name, variety_name, planting_date, expected_harvest_date, status
            FROM crop_batch
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY planting_date DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class),
            @Arg(column = "variety_name", javaType = String.class),
            @Arg(column = "planting_date", javaType = LocalDate.class),
            @Arg(column = "expected_harvest_date", javaType = LocalDate.class),
            @Arg(column = "status", javaType = String.class)
    })
    CropRow findLatestCrop(@Param("greenhouseId") Long greenhouseId);

    record GreenhouseRow(
            Long greenhouseId,
            String greenhouseCode,
            String greenhouseName,
            BigDecimal area,
            String location,
            String status
    ) {
    }

    record CycleRow(
            Long cycleId,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd,
            String dataDigest,
            String rootHash,
            String chainStatus
    ) {
    }

    record MetricRow(String metricCode, BigDecimal metricValue) {
    }

    record DeviceRow(
            Long deviceId,
            String deviceCode,
            String deviceType,
            String onlineStatus,
            String operatingState,
            LocalDateTime lastSeenAt
    ) {
    }

    record CropRow(
            Long batchId,
            String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate,
            String status
    ) {
    }
}
