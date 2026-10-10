package com.smartgreenhouse.api.modules.alert;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AlertView>> list(
            @RequestParam(required = false) String greenhouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(
                service.list(greenhouseCode, status, limit)
        );
    }

    @PostMapping("/{alertId}/ack")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<AlertView> acknowledge(
            @PathVariable Long alertId,
            @Valid @RequestBody AckRequest request
    ) {
        return ApiResponse.success(
                service.acknowledge(alertId, request.operator())
        );
    }

    public record AckRequest(@NotBlank String operator) {
    }
}
