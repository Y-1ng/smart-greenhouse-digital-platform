package com.smartgreenhouse.api.modules.telemetry;

import com.smartgreenhouse.common.api.ApiResponse;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 设备状态上报接口：接收物联网服务从 MQTT 状态主题转发上来的在线/离线与自检结果。
 */
@RestController
@RequestMapping("/api/iot/devices")
public class IotDeviceStatusController {

    private final IotTelemetryMapper mapper;

    public IotDeviceStatusController(IotTelemetryMapper mapper) {
        this.mapper = mapper;
    }

    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'IOT')")
    public ApiResponse<DeviceStatusView> report(
            @Valid @RequestBody DeviceStatusRequest request
    ) {
        IotTelemetryMapper.GreenhouseRow greenhouse =
                mapper.findGreenhouseByCode(request.greenhouseCode());
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        Long deviceId = mapper.findDeviceId(
                greenhouse.greenhouseId(),
                request.deviceCode()
        );
        if (deviceId == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "设备不存在或不属于该温室"
            );
        }

        String onlineStatus = Boolean.FALSE.equals(request.online())
                ? "OFFLINE"
                : "ONLINE";
        LocalDateTime lastSeenAt = request.lastSeenAt() == null
                || request.lastSeenAt().isBlank()
                ? LocalDateTime.now()
                : LocalDateTime.parse(request.lastSeenAt());
        mapper.updateDeviceStatus(
                greenhouse.greenhouseId(),
                request.deviceCode(),
                onlineStatus,
                lastSeenAt
        );
        return ApiResponse.success(new DeviceStatusView(
                greenhouse.greenhouseCode(),
                request.deviceCode(),
                onlineStatus,
                lastSeenAt
        ));
    }

    public record DeviceStatusRequest(
            @NotBlank String greenhouseCode,
            @NotBlank String deviceCode,
            @NotNull Boolean online,
            String lastSeenAt,
            String message
    ) {
    }

    public record DeviceStatusView(
            String greenhouseCode,
            String deviceCode,
            String onlineStatus,
            LocalDateTime lastSeenAt
    ) {
    }
}
