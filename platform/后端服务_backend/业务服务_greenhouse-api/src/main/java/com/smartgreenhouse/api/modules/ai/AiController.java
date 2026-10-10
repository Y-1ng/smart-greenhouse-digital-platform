package com.smartgreenhouse.api.modules.ai;

import com.smartgreenhouse.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 总结与 AI 问答接口。
 * 所有接口都要求登录（SecurityConfig 里 /api/** 默认需要认证）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService service;

    public AiController(AiAssistantService service) {
        this.service = service;
    }

    /** 前端用它展示当前是真实大模型还是本地兜底 */
    @GetMapping("/status")
    public ApiResponse<AiStatusView> status() {
        return ApiResponse.success(service.status());
    }

    /** AI 总结：按框架归纳平台真实数据 */
    @PostMapping("/summary")
    public ApiResponse<AiSummaryView> summary(
            @Valid @RequestBody SummaryRequest request
    ) {
        return ApiResponse.success(service.summarize(
                request.scopeTitle(),
                request.framework(),
                request.instruction()
        ));
    }

    /** AI 问答：按会话读取历史 */
    @GetMapping("/chat/history")
    public ApiResponse<List<AiChatMessageView>> history(
            @RequestParam String sessionId
    ) {
        return ApiResponse.success(service.history(sessionId));
    }

    /** AI 问答：提问并返回这一问一答 */
    @PostMapping("/chat")
    public ApiResponse<AiChatReplyView> chat(
            @Valid @RequestBody ChatRequest request
    ) {
        return ApiResponse.success(service.chat(
                request.sessionId(),
                request.question(),
                request.operator()
        ));
    }

    /** AI 问答：清空当前会话 */
    @DeleteMapping("/chat/history")
    public ApiResponse<Integer> clear(@RequestParam String sessionId) {
        return ApiResponse.success(service.clear(sessionId));
    }

    public record SummaryRequest(
            String scopeTitle,
            String framework,
            String instruction
    ) {
    }

    public record ChatRequest(
            @NotBlank String sessionId,
            @NotBlank String question,
            String operator
    ) {
    }
}
