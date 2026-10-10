package com.smartgreenhouse.api.integration.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * 链上业务键命名空间。
 *
 * 演示环境每次使用内存数据库重建业务数据，而 FISCO 链上历史会永久保留。
 * 开启 auto 模式后，每次进程启动生成独立前缀，避免本轮业务键与上一轮链上记录冲突；
 * 默认配置不添加前缀，真实持久化数据库仍使用原始业务键。
 */
@Component
public class ChainBusinessNamespace {

    private static final Logger log =
            LoggerFactory.getLogger(ChainBusinessNamespace.class);

    private static final String AUTO_MODE = "auto";

    private final String prefix;

    public ChainBusinessNamespace(
            @Value("${app.chain.namespace:}") String configuredNamespace,
            @Value("${app.chain.namespace-mode:none}") String namespaceMode
    ) {
        this.prefix = resolvePrefix(configuredNamespace, namespaceMode);
        if (!prefix.isEmpty()) {
            log.info("链上业务命名空间已启用：{}", prefix);
        }
    }

    public String qualify(String businessKey) {
        if (businessKey == null || businessKey.isBlank() || prefix.isEmpty()) {
            return businessKey;
        }
        return prefix + businessKey;
    }

    private String resolvePrefix(
            String configuredNamespace,
            String namespaceMode
    ) {
        String mode = namespaceMode == null
                ? ""
                : namespaceMode.trim().toLowerCase(Locale.ROOT);
        if (AUTO_MODE.equals(mode)) {
            return "demo-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12) + "-";
        }
        String namespace = configuredNamespace == null
                ? ""
                : configuredNamespace.trim();
        if (namespace.isEmpty()) {
            return "";
        }
        return namespace.endsWith("-") ? namespace : namespace + "-";
    }
}
