package com.smartgreenhouse.api.modules.trace;

import com.smartgreenhouse.common.api.ApiResponse;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/trace-codes")
public class TraceCodeController {

    private final TraceCodeService service;
    private final TraceQrCodeService qrCodeService;

    public TraceCodeController(
            TraceCodeService service,
            TraceQrCodeService qrCodeService
    ) {
        this.service = service;
        this.qrCodeService = qrCodeService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'QUALITY')")
    public ApiResponse<TraceCodeView> create(
            @Valid @RequestBody CreateRequest request
    ) {
        return ApiResponse.success(service.create(request));
    }

    /**
     * 二维码载荷：前端预览用，保证预览内容与后端图片完全一致。
     */
    @GetMapping("/{productCode}/qr-payload")
    public ApiResponse<TraceQrPayloadView> qrPayload(
            @PathVariable String productCode
    ) {
        return ApiResponse.success(qrCodeService.describe(productCode));
    }

    /**
     * 二维码图片下载：{@code format=png|svg}，size 可选（96–1024）。
     * 图片本身只包含公开的校验地址，因此允许匿名下载，便于打印与第三方标签工具调用。
     */
    @GetMapping("/{productCode}/qrcode")
    public ResponseEntity<byte[]> qrcode(
            @PathVariable String productCode,
            @RequestParam(defaultValue = "png") String format,
            @RequestParam(required = false) Integer size
    ) {
        String normalized = format == null
                ? "png"
                : format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "png" -> ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            attachmentName("png", productCode)
                    )
                    .body(qrCodeService.renderPng(productCode, size));
            case "svg" -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            attachmentName("svg", productCode)
                    )
                    .body(
                            qrCodeService.renderSvg(productCode, size)
                                    .getBytes(StandardCharsets.UTF_8)
                    );
            default -> throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "format 仅支持 png 或 svg"
            );
        };
    }

    private String attachmentName(String extension, String productCode) {
        String safe = productCode == null
                ? "trace-code"
                : productCode.replaceAll("[^0-9A-Za-z._-]", "_");
        return "attachment; filename=\"trace-" + safe + "." + extension + "\"";
    }

    public record CreateRequest(
            @NotNull Long batchId,
            @NotBlank String fieldNo,
            @NotBlank String productCode,
            @NotBlank String publicKeyId,
            @NotBlank String signatureValue
    ) {
    }
}
