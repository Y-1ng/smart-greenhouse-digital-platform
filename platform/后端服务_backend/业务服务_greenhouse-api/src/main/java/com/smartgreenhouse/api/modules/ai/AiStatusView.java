package com.smartgreenhouse.api.modules.ai;

/**
 * 前端用它显示"当前是真实大模型还是本地兜底"。
 *
 * @param provider      提供方名称，例如 DeepSeek
 * @param model         模型名，例如 deepseek-chat
 * @param configured    是否配置了真实密钥
 * @param modelAvailable 容器里是否真的拿到了 ChatModel
 * @param mode          deepseek=真实调用；local=本地框架结论
 */
public record AiStatusView(
        String provider,
        String model,
        boolean configured,
        boolean modelAvailable,
        String mode
) {
}
