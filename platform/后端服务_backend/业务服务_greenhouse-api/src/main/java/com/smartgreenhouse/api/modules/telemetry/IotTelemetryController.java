package com.smartgreenhouse.api.modules.telemetry;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

@RestController
@RequestMapping("/api/iot/telemetry")
public class IotTelemetryController {

    private final IotTelemetryService service;

    public IotTelemetryController(IotTelemetryService service) {
        this.service = service;
    }

    @PostMapping("/readings")
    @PreAuthorize("hasAnyRole('ADMIN', 'IOT')")
    public ApiResponse<IotTelemetryView> ingest(
            @Valid @RequestBody IngestRequest request
    ) {
        return ApiResponse.success(service.ingest(request));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'IOT', 'OPERATOR')")
    public ApiResponse<List<IotTelemetryView>> latest(
            @RequestParam String greenhouseCode,
            @RequestParam(required = false) String metricCode
    ) {
        return ApiResponse.success(
                service.latest(greenhouseCode, metricCode)
        );
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'IOT', 'OPERATOR')")
    public ApiResponse<IotTelemetryHistory> history(
            @RequestParam String greenhouseCode,
            @RequestParam(defaultValue = "30") int minutes,
            @RequestParam(defaultValue = "20") int bucketSeconds
    ) {
        return ApiResponse.success(
                service.history(greenhouseCode, minutes, bucketSeconds)
        );
    }

    public record IngestRequest(
            @NotBlank String greenhouseCode,
            @NotBlank String deviceCode,
            @NotBlank String metricCode,
            @NotNull BigDecimal value,
            String unit,
            @NotNull LocalDateTime collectedAt,
            String topic
    ) {
    }
}
