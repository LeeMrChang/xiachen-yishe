package com.xiachen.yishe.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiachen.yishe.common.cache.CacheKeys;
import com.xiachen.yishe.common.cache.CacheTtl;
import com.xiachen.yishe.common.cache.JsonCache;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.common.web.PageResult;
import com.xiachen.yishe.api.security.UserContext;
import com.xiachen.yishe.api.service.FootprintService;
import com.xiachen.yishe.api.service.TryonService;
import com.xiachen.yishe.api.service.UserMarkService;
import com.xiachen.yishe.domain.service.CatalogService;
import com.xiachen.yishe.domain.view.ProductCard;
import com.xiachen.yishe.domain.view.ProductDetailView;
import com.xiachen.yishe.domain.view.ProductFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 50;

    private final CatalogService catalogService;
    private final UserMarkService markService;
    private final TryonService tryonService;
    private final FootprintService footprintService;
    private final JsonCache cache;

    public ProductController(CatalogService catalogService, UserMarkService markService,
                             TryonService tryonService, FootprintService footprintService, JsonCache cache) {
        this.catalogService = catalogService;
        this.markService = markService;
        this.tryonService = tryonService;
        this.footprintService = footprintService;
        this.cache = cache;
    }

    @GetMapping
    public ApiResponse<PageResult<ProductCard>> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean onlyStock,
            @RequestParam(defaultValue = "recommend") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Long effectiveStoreId = storeId != null ? storeId : catalogService.defaultStoreId();
        String params = String.join("|", String.valueOf(category), String.valueOf(color),
                String.valueOf(size), String.valueOf(material), String.valueOf(keyword),
                String.valueOf(onlyStock), String.valueOf(sort), String.valueOf(page), String.valueOf(safeSize));
        String key = CacheKeys.list(effectiveStoreId, params);
        PageResult<ProductCard> cached = cache.get(key, new TypeReference<>() {
        });
        if (cached != null) {
            return ApiResponse.ok(cached);
        }
        List<ProductCard> all = catalogService.products(new ProductFilter(effectiveStoreId, category, color,
                size, material, keyword, onlyStock, sort));
        int from = Math.min((page - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        PageResult<ProductCard> result = new PageResult<>(all.subList(from, to), all.size(), page, safeSize);
        cache.put(key, result, Duration.ofSeconds(CacheTtl.jitter(CacheTtl.LIST)));
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailView> detail(@PathVariable Long id,
                                                 @RequestParam(required = false) Long storeId) {
        Long effectiveStoreId = storeId != null ? storeId : catalogService.defaultStoreId();
        String key = CacheKeys.product(id, effectiveStoreId);
        ProductDetailView view = cache.get(key, new TypeReference<>() {
        });
        if (view == null) {
            view = catalogService.product(id, effectiveStoreId);
            if (view == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            cache.put(key, view, Duration.ofSeconds(CacheTtl.jitter(CacheTtl.PRODUCT)));
        }
        // 缓存里只放匿名视图，登录态的收藏/点赞/清单状态在返回前叠加，避免不同用户互相污染
        Long userId = UserContext.userId();
        if (userId != null) {
            view = view.withUserState(
                    markService.has(userId, UserMarkService.Kind.FAVORITE, UserMarkService.TargetType.PRODUCT, id),
                    markService.has(userId, UserMarkService.Kind.LIKE, UserMarkService.TargetType.PRODUCT, id),
                    tryonService.hasProduct(userId, id, effectiveStoreId));
            footprintService.record(userId, id, effectiveStoreId);
        }
        return ApiResponse.ok(view);
    }
}
