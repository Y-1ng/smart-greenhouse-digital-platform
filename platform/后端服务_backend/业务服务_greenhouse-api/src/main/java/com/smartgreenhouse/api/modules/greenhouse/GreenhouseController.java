package com.smartgreenhouse.api.modules.greenhouse;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/greenhouses")
public class GreenhouseController {

    private final GreenhouseOverviewService service;
    private final GreenhouseManagementService managementService;

    public GreenhouseController(
            GreenhouseOverviewService service,
            GreenhouseManagementService managementService
    ) {
        this.service = service;
        this.managementService = managementService;
    }

    @GetMapping
    public ApiResponse<List<GreenhouseSummary>> list() {
        return ApiResponse.success(service.listGreenhouses());
    }

    @GetMapping("/{greenhouseCode}")
    public ApiResponse<GreenhouseOverview> getOverview(
            @PathVariable String greenhouseCode
    ) {
        return ApiResponse.success(service.getOverview(greenhouseCode));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<GreenhouseSummary> create(
            @Valid @RequestBody CreateGreenhouseRequest request
    ) {
        return ApiResponse.success(managementService.create(
                new GreenhouseManagementService.CreateGreenhouseCommand(
                        request.code(),
                        request.name(),
                        request.area(),
                        request.location(),
                        request.cropName(),
                        request.varietyName(),
                        request.plantingDate(),
                        request.expectedHarvestDate()
                )
        ));
    }

    @PutMapping("/{greenhouseCode}/crop")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<GreenhouseSummary> updateCrop(
            @PathVariable String greenhouseCode,
            @Valid @RequestBody UpdateCropRequest request
    ) {
        return ApiResponse.success(managementService.updateCrop(
                greenhouseCode,
                new GreenhouseManagementService.CropCommand(
                        request.cropName(),
                        request.varietyName(),
                        request.plantingDate(),
                        request.expectedHarvestDate()
                )
        ));
    }

    @DeleteMapping("/{greenhouseCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<Void> delete(
            @PathVariable String greenhouseCode
    ) {
        managementService.delete(greenhouseCode);
        return ApiResponse.success(null);
    }

    public record CreateGreenhouseRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @DecimalMin("0.01") BigDecimal area,
            String location,
            @NotBlank String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate
    ) {
    }

    public record UpdateCropRequest(
            @NotBlank String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate
    ) {
    }
}
