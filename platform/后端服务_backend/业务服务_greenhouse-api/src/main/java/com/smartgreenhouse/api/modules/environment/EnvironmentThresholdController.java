package com.smartgreenhouse.api.modules.environment;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/environment/thresholds")
public class EnvironmentThresholdController {

    private final EnvironmentThresholdService service;

    public EnvironmentThresholdController(EnvironmentThresholdService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<EnvironmentThresholdView>> list(
            @RequestParam(defaultValue = "GH-001") String greenhouseCode
    ) {
        return ApiResponse.success(service.list(greenhouseCode));
    }

    @PutMapping("/{metricCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AGRONOMIST')")
    public ApiResponse<EnvironmentThresholdView> update(
            @PathVariable String metricCode,
            @Valid @RequestBody UpdateRequest request
    ) {
        return ApiResponse.success(service.update(metricCode, request));
    }

    public record UpdateRequest(
            @NotBlank String greenhouseCode,
            @NotNull BigDecimal targetMin,
            @NotNull BigDecimal targetMax,
            @NotNull BigDecimal alertMin,
            @NotNull BigDecimal alertMax,
            @NotBlank String controlAction,
            @NotNull Boolean enabled
    ) {
    }
}
