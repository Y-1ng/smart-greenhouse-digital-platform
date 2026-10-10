package com.smartgreenhouse.api.modules.cooperation;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/cooperation")
public class CooperationController {

    private final CooperationService service;

    public CooperationController(CooperationService service) {
        this.service = service;
    }

    @GetMapping("/villages")
    public ApiResponse<List<VillageSummary>> villages() {
        return ApiResponse.success(service.listVillages());
    }

    @PostMapping("/shares")
    @PreAuthorize("hasAnyRole('ADMIN', 'COOPERATIVE')")
    public ApiResponse<CooperationShareView> createShare(
            @Valid @RequestBody CreateShareRequest request
    ) {
        return ApiResponse.success(service.createShare(request));
    }

    @PostMapping("/dividends")
    @PreAuthorize("hasAnyRole('ADMIN', 'COOPERATIVE')")
    public ApiResponse<DividendView> createDividend(
            @Valid @RequestBody CreateDividendRequest request
    ) {
        return ApiResponse.success(service.createDividend(request));
    }

    @DeleteMapping("/shares/{shareId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COOPERATIVE')")
    public ApiResponse<Boolean> deleteShare(
            @PathVariable Long shareId
    ) {
        service.deleteShare(shareId);
        return ApiResponse.success(true);
    }

    public record CreateShareRequest(
            Long villageId,
            String villageName,
            @NotBlank String projectName,
            @NotNull @DecimalMin("0.0001") @DecimalMax("1.0000")
            BigDecimal shareRatio,
            @NotNull LocalDate effectiveDate,
            String status
    ) {
    }

    public record CreateDividendRequest(
            @NotNull Long shareId,
            @NotBlank String periodName,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String status
    ) {
    }
}
