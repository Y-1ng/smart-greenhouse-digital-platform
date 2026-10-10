package com.smartgreenhouse.api.modules.dashboard;

import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 政府监管和产业治理驾驶舱接口。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/government")
    @PreAuthorize("hasAnyRole('ADMIN', 'GOVERNMENT')")
    public ApiResponse<GovernmentDashboardView> government() {
        return ApiResponse.success(service.governmentDashboard());
    }
}
