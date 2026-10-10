package com.smartgreenhouse.api.integration.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 缓存层必须满足两条底线：关闭时不碰 Redis；Redis 异常时不把错误抛给业务。
 */
class JsonCacheTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations =
            mock(ValueOperations.class);

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void doesNotTouchRedisWhenDisabled() {
        JsonCache cache = new JsonCache(
                redisTemplate,
                objectMapper,
                false,
                "test:"
        );

        assertThat(cache.isEnabled()).isFalse();
        assertThat(cache.get("k", String.class)).isEmpty();
        cache.put("k", "v", Duration.ofSeconds(5));
        cache.evict("k");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void readsCachedJsonWhenEnabled() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test:overview:GH-001"))
                .thenReturn("{\"code\":\"GH-001\"}");
        JsonCache cache = new JsonCache(
                redisTemplate,
                objectMapper,
                true,
                "test:"
        );

        Optional<CachedSample> cached =
                cache.get("overview:GH-001", CachedSample.class);

        assertThat(cached).isPresent();
        assertThat(cached.get().code()).isEqualTo("GH-001");
    }

    @Test
    void degradesToDatabaseWhenRedisFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));
        JsonCache cache = new JsonCache(
                redisTemplate,
                objectMapper,
                true,
                "test:"
        );

        assertThat(cache.get("k", CachedSample.class)).isEmpty();
        assertThatCode(() ->
                cache.put("k", new CachedSample("GH-001"), Duration.ofSeconds(5))
        ).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict("k")).doesNotThrowAnyException();
        assertThat(cache.ping()).isFalse();
    }

    @Test
    void writesWithTtlWhenEnabled() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        JsonCache cache = new JsonCache(
                redisTemplate,
                objectMapper,
                true,
                "test:"
        );

        cache.put("k", new CachedSample("GH-002"), Duration.ofSeconds(7));

        verify(valueOperations).set(
                eq("test:k"),
                eq("{\"code\":\"GH-002\"}"),
                eq(Duration.ofSeconds(7))
        );
        verify(valueOperations, never()).get(anyString());
    }

    record CachedSample(String code) {
    }
}
