package com.smartgreenhouse.api.modules.health;

import com.smartgreenhouse.api.integration.cache.JsonCache;
import com.smartgreenhouse.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务健康检查接口。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "service", "greenhouse-api",
                "cacheEnabled", cache.isEnabled(),
                "cacheReachable", cache.ping()
        ));
    }

    private final JsonCache cache;

    public HealthController(JsonCache cache) {
        this.cache = cache;
    }
}
