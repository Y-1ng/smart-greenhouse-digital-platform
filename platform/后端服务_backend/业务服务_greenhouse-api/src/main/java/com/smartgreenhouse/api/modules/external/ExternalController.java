package com.smartgreenhouse.api.modules.external;

import com.smartgreenhouse.api.integration.external.ExternalPlatform;
import com.smartgreenhouse.common.api.ApiResponse;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外部平台对接接口：查看京东农场、气象、短信三个适配器的状态与调用留痕，并支持手动同步。
 */
@RestController
@RequestMapping("/api/external")
public class ExternalController {

    private final ExternalSyncService service;

    public ExternalController(ExternalSyncService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'QUALITY', 'GOVERNMENT')")
    public ApiResponse<List<ExternalViews.ExternalStatusView>> status() {
        return ApiResponse.success(service.status());
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'QUALITY', 'GOVERNMENT')")
    public ApiResponse<List<ExternalViews.ExternalLogView>> logs(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(service.logs(limit));
    }

    /**
     * 手动触发同步：platform 取 WEATHER / JD_FARM / SMS，缺省 ALL 表示三个平台都跑一遍。
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<ExternalViews.ExternalSyncReport>> sync(
            @RequestParam(defaultValue = "ALL") String platform
    ) {
        if ("ALL".equalsIgnoreCase(platform)) {
            return ApiResponse.success(service.syncAll());
        }
        try {
            return ApiResponse.success(List.of(
                    service.sync(ExternalPlatform.valueOf(platform.toUpperCase()))
            ));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "不支持的外部平台：" + platform
            );
        }
    }
}
