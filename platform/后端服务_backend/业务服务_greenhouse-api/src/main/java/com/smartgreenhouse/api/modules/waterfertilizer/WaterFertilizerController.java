package com.smartgreenhouse.api.modules.waterfertilizer;

import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/water-fertilizer")
public class WaterFertilizerController {

    private final WaterFertilizerService service;

    public WaterFertilizerController(WaterFertilizerService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    public ApiResponse<List<WaterFertilizerPlanView>> list(
            @RequestParam(defaultValue = "GH-001") String greenhouseCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return ApiResponse.success(service.list(greenhouseCode, date));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMIST')")
    public ApiResponse<WaterFertilizerPlanView> create(
            @RequestBody CreatePlanRequest request
    ) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping("/plans/{planNo}/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<WaterFertilizerPlanView> execute(
            @PathVariable String planNo,
            @RequestBody ExecutePlanRequest request
    ) {
        return ApiResponse.success(service.execute(planNo, request));
    }

    public record CreatePlanRequest(
            String greenhouseCode,
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

    public record ExecutePlanRequest(
            String operatorName,
            LocalDateTime executedAt
    ) {
    }
}
