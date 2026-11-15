package com.xiachen.yishe.admin.support;

import com.xiachen.yishe.common.cache.JsonCache;
import org.springframework.stereotype.Component;

@Component
public class CacheEvictor {

    private final JsonCache cache;

    public CacheEvictor(JsonCache cache) {
        this.cache = cache;
    }

    /** 商品/库存变更后统一失效 C 端读缓存：详情、列表、首页推荐 */
    public void evictCatalog() {
        cache.evictByPrefix("xc:product:");
        cache.evictByPrefix("xc:list:");
        cache.evictByPrefix("xc:home:");
    }
}
