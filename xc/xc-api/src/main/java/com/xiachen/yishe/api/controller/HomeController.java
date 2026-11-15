package com.xiachen.yishe.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiachen.yishe.common.cache.CacheKeys;
import com.xiachen.yishe.common.cache.CacheTtl;
import com.xiachen.yishe.common.cache.JsonCache;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.domain.service.CatalogService;
import com.xiachen.yishe.domain.view.HomeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final CatalogService catalogService;
    private final JsonCache cache;

    public HomeController(CatalogService catalogService, JsonCache cache) {
        this.catalogService = catalogService;
        this.cache = cache;
    }

    @GetMapping
    public ApiResponse<HomeView> home(@RequestParam(required = false) Long storeId) {
        Long effectiveStoreId = storeId != null ? storeId : catalogService.defaultStoreId();
        String key = CacheKeys.home(effectiveStoreId);
        HomeView cached = cache.get(key, new TypeReference<>() {
        });
        if (cached != null) {
            return ApiResponse.ok(cached);
        }
        HomeView view = catalogService.home(effectiveStoreId);
        cache.put(key, view, Duration.ofSeconds(CacheTtl.jitter(CacheTtl.HOME)));
        return ApiResponse.ok(view);
    }
}
