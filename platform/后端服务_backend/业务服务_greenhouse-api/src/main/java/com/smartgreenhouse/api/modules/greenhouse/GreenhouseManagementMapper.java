package com.smartgreenhouse.api.modules.greenhouse;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface GreenhouseManagementMapper {

    @Select("SELECT COUNT(*) FROM greenhouse WHERE greenhouse_code = #{code}")
    int countByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO greenhouse (
              greenhouse_code, greenhouse_name, area, location, status
            ) VALUES (
              #{code}, #{name}, #{area}, #{location}, 'ACTIVE'
            )
            """)
    int insertGreenhouse(
            @Param("code") String code,
            @Param("name") String name,
            @Param("area") BigDecimal area,
            @Param("location") String location
    );

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
            WHERE g.greenhouse_code = #{code}
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
    GreenhouseSummary findSummaryByCode(@Param("code") String code);

    @Select("""
            SELECT batch_id
            FROM crop_batch
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY planting_date DESC, batch_id DESC
            LIMIT 1
            """)
    Long findLatestBatchId(@Param("greenhouseId") Long greenhouseId);

    @Insert("""
            INSERT INTO crop_batch (
              greenhouse_id,
              crop_name,
              variety_name,
              planting_date,
              expected_harvest_date,
              status
            ) VALUES (
              #{greenhouseId},
              #{cropName},
              #{varietyName},
              #{plantingDate},
              #{expectedHarvestDate},
              'GROWING'
            )
            """)
    int insertCropBatch(
            @Param("greenhouseId") Long greenhouseId,
            @Param("cropName") String cropName,
            @Param("varietyName") String varietyName,
            @Param("plantingDate") LocalDate plantingDate,
            @Param("expectedHarvestDate") LocalDate expectedHarvestDate
    );

    @Update("""
            UPDATE crop_batch
            SET crop_name = #{cropName},
                variety_name = #{varietyName},
                planting_date = #{plantingDate},
                expected_harvest_date = #{expectedHarvestDate},
                status = 'GROWING'
            WHERE batch_id = #{batchId}
            """)
    int updateCropBatch(
            @Param("batchId") Long batchId,
            @Param("cropName") String cropName,
            @Param("varietyName") String varietyName,
            @Param("plantingDate") LocalDate plantingDate,
            @Param("expectedHarvestDate") LocalDate expectedHarvestDate
    );

    @Select("""
            SELECT COUNT(*)
            FROM (
              SELECT 1 FROM quality_report qr
              JOIN crop_batch cb ON cb.batch_id = qr.batch_id
              WHERE cb.greenhouse_id = #{greenhouseId}
              UNION ALL
              SELECT 1 FROM certificate c
              JOIN crop_batch cb ON cb.batch_id = c.batch_id
              WHERE cb.greenhouse_id = #{greenhouseId}
              UNION ALL
              SELECT 1 FROM trace_code tc
              JOIN crop_batch cb ON cb.batch_id = tc.batch_id
              WHERE cb.greenhouse_id = #{greenhouseId}
            ) trace_data
            """)
    long countProtectedTraceData(@Param("greenhouseId") Long greenhouseId);

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM device WHERE greenhouse_id = #{greenhouseId})
              + (SELECT COUNT(*) FROM environment_cycle WHERE greenhouse_id = #{greenhouseId})
              + (SELECT COUNT(*) FROM control_command WHERE greenhouse_id = #{greenhouseId})
              + (SELECT COUNT(*) FROM iot_telemetry WHERE greenhouse_id = #{greenhouseId})
              + (SELECT COUNT(*) FROM water_fertilizer_plan WHERE greenhouse_id = #{greenhouseId})
              + (SELECT COUNT(*) FROM alert_record WHERE greenhouse_id = #{greenhouseId})
            """)
    long countProtectedOperationalData(
            @Param("greenhouseId") Long greenhouseId
    );

    @Delete("""
            DELETE FROM environment_threshold
            WHERE greenhouse_id = #{greenhouseId}
            """)
    int deleteThresholds(@Param("greenhouseId") Long greenhouseId);

    @Delete("""
            DELETE FROM crop_batch
            WHERE greenhouse_id = #{greenhouseId}
            """)
    int deleteCropBatches(@Param("greenhouseId") Long greenhouseId);

    @Delete("DELETE FROM greenhouse WHERE greenhouse_code = #{code}")
    int deleteGreenhouse(@Param("code") String code);
}
