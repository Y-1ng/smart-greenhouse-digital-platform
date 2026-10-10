package com.smartgreenhouse.api.modules.waterfertilizer;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WaterFertilizerMapper {

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
            SELECT greenhouse_id, greenhouse_code
            FROM greenhouse
            ORDER BY greenhouse_id
            """)
    @ConstructorArgs({
            @Arg(column = "greenhouse_id", javaType = Long.class),
            @Arg(column = "greenhouse_code", javaType = String.class)
    })
    List<GreenhouseRow> findAllGreenhouses();

    @Select("""
            SELECT batch_id, crop_name
            FROM crop_batch
            WHERE batch_id = #{batchId}
              AND greenhouse_id = #{greenhouseId}
            """)
    @ConstructorArgs({
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class)
    })
    BatchRow findBatch(
            @Param("batchId") Long batchId,
            @Param("greenhouseId") Long greenhouseId
    );

    @Select("""
            SELECT batch_id, crop_name
            FROM crop_batch
            WHERE greenhouse_id = #{greenhouseId}
            ORDER BY planting_date DESC, batch_id DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class)
    })
    BatchRow findFirstBatch(@Param("greenhouseId") Long greenhouseId);

    @Select("""
            SELECT
              p.plan_id,
              p.plan_no,
              g.greenhouse_code,
              p.batch_id,
              cb.crop_name,
              p.recipe_code,
              p.recipe_version,
              p.ec_target,
              p.ph_target,
              p.zone_name,
              p.planned_at,
              p.water_volume,
              p.status,
              p.operator_name,
              p.executed_at,
              p.failure_reason
            FROM water_fertilizer_plan p
            JOIN greenhouse g ON g.greenhouse_id = p.greenhouse_id
            JOIN crop_batch cb ON cb.batch_id = p.batch_id
            WHERE g.greenhouse_code = #{greenhouseCode}
              AND DATE(p.planned_at) = #{planDate}
            ORDER BY p.planned_at, p.plan_id
            """)
    @ConstructorArgs({
            @Arg(column = "plan_id", javaType = Long.class),
            @Arg(column = "plan_no", javaType = String.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class),
            @Arg(column = "recipe_code", javaType = String.class),
            @Arg(column = "recipe_version", javaType = String.class),
            @Arg(column = "ec_target", javaType = BigDecimal.class),
            @Arg(column = "ph_target", javaType = BigDecimal.class),
            @Arg(column = "zone_name", javaType = String.class),
            @Arg(column = "planned_at", javaType = LocalDateTime.class),
            @Arg(column = "water_volume", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "executed_at", javaType = LocalDateTime.class),
            @Arg(column = "failure_reason", javaType = String.class)
    })
    List<PlanRow> findPlans(
            @Param("greenhouseCode") String greenhouseCode,
            @Param("planDate") LocalDate planDate
    );

    @Select("""
            SELECT
              p.plan_id,
              p.plan_no,
              g.greenhouse_code,
              p.batch_id,
              cb.crop_name,
              p.recipe_code,
              p.recipe_version,
              p.ec_target,
              p.ph_target,
              p.zone_name,
              p.planned_at,
              p.water_volume,
              p.status,
              p.operator_name,
              p.executed_at,
              p.failure_reason
            FROM water_fertilizer_plan p
            JOIN greenhouse g ON g.greenhouse_id = p.greenhouse_id
            JOIN crop_batch cb ON cb.batch_id = p.batch_id
            WHERE p.plan_no = #{planNo}
            """)
    @ConstructorArgs({
            @Arg(column = "plan_id", javaType = Long.class),
            @Arg(column = "plan_no", javaType = String.class),
            @Arg(column = "greenhouse_code", javaType = String.class),
            @Arg(column = "batch_id", javaType = Long.class),
            @Arg(column = "crop_name", javaType = String.class),
            @Arg(column = "recipe_code", javaType = String.class),
            @Arg(column = "recipe_version", javaType = String.class),
            @Arg(column = "ec_target", javaType = BigDecimal.class),
            @Arg(column = "ph_target", javaType = BigDecimal.class),
            @Arg(column = "zone_name", javaType = String.class),
            @Arg(column = "planned_at", javaType = LocalDateTime.class),
            @Arg(column = "water_volume", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "operator_name", javaType = String.class),
            @Arg(column = "executed_at", javaType = LocalDateTime.class),
            @Arg(column = "failure_reason", javaType = String.class)
    })
    PlanRow findByPlanNo(@Param("planNo") String planNo);

    @Select("""
            SELECT COUNT(*)
            FROM water_fertilizer_plan
            WHERE plan_no LIKE #{prefix}
            """)
    int countByPlanNoPrefix(@Param("prefix") String prefix);

    @Insert("""
            INSERT INTO water_fertilizer_plan (
              plan_no,
              greenhouse_id,
              batch_id,
              recipe_code,
              recipe_version,
              ec_target,
              ph_target,
              zone_name,
              planned_at,
              water_volume,
              status,
              operator_name
            ) VALUES (
              #{planNo},
              #{greenhouseId},
              #{batchId},
              #{recipeCode},
              #{recipeVersion},
              #{ecTarget},
              #{phTarget},
              #{zoneName},
              #{plannedAt},
              #{waterVolume},
              'PLANNED',
              #{operatorName}
            )
            """)
    int insert(PlanInsert plan);

    @Update("""
            UPDATE water_fertilizer_plan
            SET
              status = 'SUCCEEDED',
              operator_name = #{operatorName},
              executed_at = #{executedAt},
              failure_reason = NULL
            WHERE plan_no = #{planNo}
              AND status = 'PLANNED'
            """)
    int markSucceeded(
            @Param("planNo") String planNo,
            @Param("operatorName") String operatorName,
            @Param("executedAt") LocalDateTime executedAt
    );

    record GreenhouseRow(Long greenhouseId, String greenhouseCode) {
    }

    record BatchRow(Long batchId, String cropName) {
    }

    record PlanRow(
            Long planId,
            String planNo,
            String greenhouseCode,
            Long batchId,
            String cropName,
            String recipeCode,
            String recipeVersion,
            BigDecimal ecTarget,
            BigDecimal phTarget,
            String zoneName,
            LocalDateTime plannedAt,
            BigDecimal waterVolume,
            String status,
            String operatorName,
            LocalDateTime executedAt,
            String failureReason
    ) {
    }

    record PlanInsert(
            String planNo,
            Long greenhouseId,
            Long batchId,
            String recipeCode,
            String recipeVersion,
            BigDecimal ecTarget,
            BigDecimal phTarget,
            String zoneName,
            LocalDateTime plannedAt,
            BigDecimal waterVolume,
            String operatorName
    ) {
    }
}
