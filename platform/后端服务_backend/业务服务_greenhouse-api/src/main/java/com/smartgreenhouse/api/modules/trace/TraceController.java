package com.smartgreenhouse.api.modules.trace;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一物一码查询与扫码验证接口。
 */
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceService service;

    public TraceController(TraceService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    public ApiResponse<TraceView> trace(@PathVariable String code) {
        return ApiResponse.success(service.getTrace(code));
    }

    @GetMapping("/field/{fieldNo}/path")
    public ApiResponse<TracePathView> fieldPath(
            @PathVariable String fieldNo
    ) {
        return ApiResponse.success(service.getFieldPath(fieldNo));
    }

    @PostMapping("/codes/{code}/scan")
    public ApiResponse<ScanResultView> scan(
            @PathVariable String code,
            @Valid @RequestBody ScanRequest request
    ) {
        return ApiResponse.success(service.scan(code, request));
    }

    /**
     * 追加田块追溯节点，节点保存后按指针顺序上链存证。
     */
    @PostMapping("/fields/{fieldNo}/nodes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'QUALITY')")
    public ApiResponse<TraceNodeView> appendNode(
            @PathVariable String fieldNo,
            @Valid @RequestBody AppendNodeRequest request
    ) {
        return ApiResponse.success(service.appendNode(fieldNo, request));
    }

    public record AppendNodeRequest(
            @NotBlank String stageType,
            String summary,
            String payloadHash
    ) {
    }

    public record ScanRequest(
            @NotBlank String channel,
            @NotBlank String deviceFingerprint
    ) {
    }
}
