package com.smartgreenhouse.api.integration.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 对话客户端（OpenAI 兼容协议）。
 *
 * <p>项目里其它外部平台适配器（气象、短信、链上网关）都用
 * {@link RestClient} + {@code SimpleClientHttpRequestFactory}，
 * 这里沿用同一套：基于 JDK 的 HttpURLConnection，
 * 不用 Netty / JDK HttpClient 的 NIO selector，受限环境里也能正常出网。
 *
 * <p>请求体与响应体字段按 OpenAI 兼容协议组织，换 Kimi、通义千问只需改
 * {@code app.ai.base-url} 与 {@code app.ai.model-label}。
 */
@Component
public class DeepSeekChatClient {

    /** 占位密钥：说明没配置，调用方应直接走本地兜底 */
    public static final String PLACEHOLDER_KEY = "sk-not-configured";

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String chatPath;
    private final double temperature;
    private final int maxTokens;

    public DeepSeekChatClient(
            @Value("${app.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.chat-path:/chat/completions}") String chatPath,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model-label:deepseek-chat}") String model,
            @Value("${app.ai.temperature:0.5}") double temperature,
            @Value("${app.ai.max-tokens:1600}") int maxTokens,
            @Value("${app.ai.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms:60000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        factory.setReadTimeout(Math.max(5000, readTimeoutMs));
        this.client = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank()
                        ? "https://api.deepseek.com"
                        : baseUrl)
                .requestFactory(factory)
                .build();
        this.chatPath = chatPath == null || chatPath.isBlank()
                ? "/chat/completions"
                : chatPath;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /** 是否配置了可用的真实密钥 */
    public boolean configured() {
        return apiKey != null
                && apiKey.startsWith("sk-")
                && !apiKey.contains(PLACEHOLDER_KEY);
    }

    public String model() {
        return model;
    }

    /**
     * 同步对话补全。
     *
     * @param systemPrompt 系统提示词
     * @param history      历史消息（已按时间正序）
     * @param userContent  本次用户输入
     */
    public Completion complete(
            String systemPrompt,
            List<ChatTurn> history,
            String userContent
    ) {
        if (!configured()) {
            throw new AiCallException("未配置 DEEPSEEK_API_KEY");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message(ROLE_SYSTEM, systemPrompt));
        for (ChatTurn turn : history) {
            messages.add(message(
                    ROLE_ASSISTANT.equals(turn.role()) ? ROLE_ASSISTANT : ROLE_USER,
                    turn.content()
            ));
        }
        messages.add(message(ROLE_USER, userContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        long startedAt = System.currentTimeMillis();
        try {
            ChatCompletionResponse response = client.post()
                    .uri(chatPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = firstContent(response);
            long latencyMs = System.currentTimeMillis() - startedAt;
            String resolvedModel = response == null || response.model() == null
                    ? model
                    : response.model();
            return new Completion(content, resolvedModel, latencyMs);
        } catch (RestClientResponseException exception) {
            throw new AiCallException(
                    "HTTP " + exception.getStatusCode().value()
                            + "：" + brief(exception.getResponseBodyAsString()),
                    exception
            );
        } catch (AiCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiCallException(brief(exception.getMessage()), exception);
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private String firstContent(ChatCompletionResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new AiCallException("模型没有返回内容");
        }
        return response.choices().get(0).message().content().trim();
    }

    private String brief(String message) {
        if (message == null || message.isBlank()) {
            return "无错误详情";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 180);
    }

    /** 一条历史消息 */
    public record ChatTurn(String role, String content) {
    }

    /** 一次补全结果 */
    public record Completion(String content, String model, long latencyMs) {
    }

    /** 调用失败（网络、鉴权、限流、返回体异常等） */
    public static class AiCallException extends RuntimeException {

        public AiCallException(String message) {
            super(message);
        }

        public AiCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ------------------------------------------------------------------
    // OpenAI 兼容协议的响应结构（只取用得到的字段）
    // ------------------------------------------------------------------

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(
            String id,
            String model,
            List<Choice> choices
    ) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            ResponseMessage message
    ) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseMessage(
            String role,
            String content
    ) {
    }
}
