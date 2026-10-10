package com.smartgreenhouse.api.modules.waterfertilizer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WaterFertilizerPlanView(
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
