package com.smartgreenhouse.api.modules.crop;

import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crop-batches")
public class CropBatchController {

    private final CropBatchService service;

    public CropBatchController(CropBatchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ApiResponse<List<CropBatchView>> list() {
        return ApiResponse.success(service.list());
    }
}
