package com.smartgreenhouse.api.modules.control;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能控制接口，负责生成设备指令并跟踪执行状态。
 */
@RestController
@RequestMapping("/api/control/commands")
public class ControlController {

    private final ControlCommandService service;

    public ControlController(ControlCommandService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ControlCommandView> create(
            @Valid @RequestBody ControlCommandRequest request
    ) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/{commandNo}")
    public ApiResponse<ControlCommandView> get(
            @PathVariable String commandNo
    ) {
        return ApiResponse.success(service.get(commandNo));
    }

    @PostMapping("/{commandNo}/ack")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'IOT')")
    public ApiResponse<ControlCommandView> acknowledge(
            @PathVariable String commandNo,
            @Valid @RequestBody AckRequest request
    ) {
        return ApiResponse.success(service.acknowledge(commandNo, request));
    }

    /**
     * 待下发指令列表，供物联网接入服务轮询后发布到 MQTT。
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'IOT')")
    public ApiResponse<java.util.List<ControlCommandView>> pending(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(service.pending(limit));
    }

    /**
     * 标记指令已发布到设备通道，由物联网接入服务在发布成功后调用。
     */
    @PostMapping("/{commandNo}/dispatch")
    @PreAuthorize("hasAnyRole('ADMIN', 'IOT')")
    public ApiResponse<ControlCommandView> markDispatched(
            @PathVariable String commandNo
    ) {
        return ApiResponse.success(service.markDispatched(commandNo));
    }

    /**
     * 已下发未回执的指令列表。
     */
    @GetMapping("/dispatched")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'IOT')")
    public ApiResponse<java.util.List<ControlCommandView>> dispatched(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(service.dispatched(limit));
    }

    public record ControlCommandRequest(
            @NotBlank String greenhouseCode,
            @NotBlank String deviceCode,
            @NotBlank String commandType,
            Map<String, Object> parameters,
            @NotBlank String operator,
            @Positive Integer timeoutSeconds
    ) {
    }

    public record AckRequest(
            @NotNull Boolean success,
            String message,
            String executedAt
    ) {
    }
}
