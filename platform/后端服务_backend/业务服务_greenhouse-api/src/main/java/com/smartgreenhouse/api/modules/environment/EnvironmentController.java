package com.smartgreenhouse.api.modules.environment;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 环境监测接口，提供最新快照和周期趋势。
 */
@RestController
@RequestMapping("/api/environment")
public class EnvironmentController {

    private final EnvironmentService service;

    public EnvironmentController(EnvironmentService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public ApiResponse<EnvironmentSnapshot> latest(
            @RequestParam(defaultValue = "GH-001") String greenhouseId
    ) {
        return ApiResponse.success(service.getLatest(greenhouseId));
    }

    @GetMapping("/trend")
    public ApiResponse<EnvironmentTrend> trend(
            @RequestParam(defaultValue = "GH-001") String greenhouseCode,
            @RequestParam(defaultValue = "24") int limit
    ) {
        return ApiResponse.success(service.getTrend(greenhouseCode, limit));
    }

    @PostMapping("/cycles")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<EnvironmentCycleView> ingestCycle(
            @Valid @RequestBody CycleIngestRequest request
    ) {
        return ApiResponse.success(service.ingestCycle(request));
    }

    @PostMapping("/cycles/attest")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<EnvironmentCycleView> attestFromTelemetry(
            @Valid @RequestBody AttestRequest request
    ) {
        return ApiResponse.success(
                service.attestFromTelemetry(request.greenhouseCode())
        );
    }

    public record AttestRequest(@NotBlank String greenhouseCode) {
    }

    public record CycleIngestRequest(
            @NotBlank String greenhouseCode,
            @NotNull LocalDateTime cycleStart,
            @NotNull LocalDateTime cycleEnd,
            @NotEmpty List<@Valid ReadingRequest> readings
    ) {
    }

    public record ReadingRequest(
            @NotBlank String deviceCode,
            @NotBlank String metricCode,
            @NotNull @Positive BigDecimal value,
            String unit,
            @NotNull LocalDateTime collectedAt
    ) {
    }
}
