package com.smartgreenhouse.api.modules.proof;

import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链上存证和链下原文核验接口。
 */
@RestController
@RequestMapping("/api/proofs")
public class ProofController {

    private final ProofService service;

    public ProofController(ProofService service) {
        this.service = service;
    }

    @GetMapping("/{batchId}")
    public ApiResponse<ProofView> query(@PathVariable Long batchId) {
        return ApiResponse.success(service.getProof(batchId));
    }
}
