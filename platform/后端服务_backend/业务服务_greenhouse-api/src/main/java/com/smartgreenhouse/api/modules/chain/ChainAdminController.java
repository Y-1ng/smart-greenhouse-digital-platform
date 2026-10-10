package com.smartgreenhouse.api.modules.chain;

import com.smartgreenhouse.api.integration.chain.ChainAdapterProbe;
import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链上运行管理：查看适配服务状态与待补偿数量，手工触发补偿。
 */
@RestController
@RequestMapping("/api/chain")
public class ChainAdminController {

    private final ChainCompensationService compensationService;
    private final ChainAdapterProbe adapterProbe;

    public ChainAdminController(
            ChainCompensationService compensationService,
            ChainAdapterProbe adapterProbe
    ) {
        this.compensationService = compensationService;
        this.adapterProbe = adapterProbe;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'QUALITY', 'GOVERNMENT')")
    public ApiResponse<ChainStatusView> status() {
        return ApiResponse.success(new ChainStatusView(
                adapterProbe.probe(),
                compensationService.countPending(),
                compensationService.countProofs(),
                adapterProbe.probeBlock()
        ));
    }

    @PostMapping("/compensate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ChainCompensationReport> compensate() {
        return ApiResponse.success(compensationService.compensate());
    }
}
