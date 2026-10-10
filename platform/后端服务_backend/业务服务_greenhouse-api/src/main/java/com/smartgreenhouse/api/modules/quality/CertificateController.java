package com.smartgreenhouse.api.modules.quality;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateVerifyService service;
    private final CertificateManagementService managementService;

    public CertificateController(
            CertificateVerifyService service,
            CertificateManagementService managementService
    ) {
        this.service = service;
        this.managementService = managementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ApiResponse<CertificateView> create(
            @Valid @RequestBody CertificateCreateRequest request
    ) {
        return ApiResponse.success(managementService.create(request));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ApiResponse<CertificateVerifyResult> verify(
            @Valid @RequestBody VerifyRequest request
    ) {
        return ApiResponse.success(service.verify(request));
    }

    public record VerifyRequest(
            @NotBlank String requestNo,
            @NotBlank String certificateNo,
            @NotNull Long batchId,
            @NotBlank String institutionId,
            @NotBlank String contentHash,
            @NotBlank String issuedAt,
            @NotBlank String publicKeyId,
            @NotBlank String publicKey,
            @NotBlank String signature
    ) {
    }

    public record CertificateCreateRequest(
            @NotNull Long batchId,
            @NotBlank String certificateNo,
            @NotBlank String contentHash,
            String status
    ) {
    }
}
