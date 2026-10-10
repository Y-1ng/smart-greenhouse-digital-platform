package com.smartgreenhouse.api.integration.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 轻量 JSON 缓存：Redis 可用时缓存聚合查询结果，未启用或 Redis 不可用时静默退化为直查数据库。
 *
 * 默认关闭（app.cache.enabled=false），这样没有启动 Redis 的演示环境不会因为连接超时而变慢；
 * 需要缓存时设置 CACHE_ENABLED=true，并保证 Redis 可访问。
 */
@Component
public class JsonCache {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String keyPrefix;

    // 类中另有供测试使用的构造器，这里显式标注注入入口，避免 Spring 找不到构造器
    @Autowired
    public JsonCache(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${app.cache.enabled:false}") boolean enabled,
            @Value("${app.cache.key-prefix:greenhouse:}") String keyPrefix
    ) {
        this(
                redisTemplateProvider.getIfAvailable(),
                objectMapper,
                enabled,
                keyPrefix
        );
    }

    JsonCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            boolean enabled,
            String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.keyPrefix = keyPrefix;
        if (enabled && redisTemplate == null) {
            log.warn("已开启缓存但未装配 Redis 客户端，缓存功能自动降级为直查数据库");
        }
    }

    public boolean isEnabled() {
        return enabled && redisTemplate != null;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            String cached = redisTemplate.opsForValue().get(keyPrefix + key);
            if (cached == null || cached.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(cached, type));
        } catch (Exception exception) {
            log.debug("缓存读取失败，降级为直查数据库：{}", exception.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    keyPrefix + key,
                    objectMapper.writeValueAsString(value),
                    ttl
            );
        } catch (Exception exception) {
            log.debug("缓存写入失败，本次忽略：{}", exception.getMessage());
        }
    }

    public void evict(String key) {
        if (!isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(keyPrefix + key);
        } catch (Exception exception) {
            log.debug("缓存删除失败，本次忽略：{}", exception.getMessage());
        }
    }

    /**
     * 探测 Redis 是否可访问，供健康检查展示；不可用时只返回 false，不抛异常。
     */
    public boolean ping() {
        if (!isEnabled()) {
            return false;
        }
        try {
            String result = redisTemplate.execute(
                    (RedisCallback<String>) RedisConnection::ping
            );
            return result != null;
        } catch (Exception exception) {
            log.debug("Redis 不可访问：{}", exception.getMessage());
            return false;
        }
    }
}
