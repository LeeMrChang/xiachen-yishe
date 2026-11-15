package com.xiachen.yishe.api.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.api.dto.UserTargetItem;
import com.xiachen.yishe.common.cache.CacheKeys;
import com.xiachen.yishe.common.cache.JsonCache;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.PageResult;
import com.xiachen.yishe.domain.entity.Look;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.mapper.LookMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import com.xiachen.yishe.domain.mapper.UserMarkMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserMarkService {

    private static final String ON = "ON";

    public enum Kind {
        FAVORITE("user_favorite", "fav_count"),
        LIKE("user_like", "like_count");

        final String table;
        final String countColumn;

        Kind(String table, String countColumn) {
            this.table = table;
            this.countColumn = countColumn;
        }
    }

    public enum TargetType {
        PRODUCT(1, "product"),
        LOOK(2, "look");

        final int code;
        final String table;

        TargetType(int code, String table) {
            this.code = code;
            this.table = table;
        }

        public static TargetType of(int code) {
            for (TargetType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new BizException(ErrorCode.PARAM_ERROR, "targetType 只能是 1（商品）或 2（造型）");
        }
    }

    private final UserMarkMapper markMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final LookMapper lookMapper;
    private final JsonCache cache;

    public UserMarkService(UserMarkMapper markMapper, ProductMapper productMapper, SkuMapper skuMapper,
                           LookMapper lookMapper, JsonCache cache) {
        this.markMapper = markMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.lookMapper = lookMapper;
        this.cache = cache;
    }

    public void add(long userId, Kind kind, TargetType type, long targetId) {
        requireTarget(type, targetId);
        int inserted = markMapper.insertIgnore(kind.table, userId, type.code, targetId);
        if (inserted > 0) {
            markMapper.bumpTargetCount(type.table, kind.countColumn, targetId, 1);
            evictCatalog();
        }
    }

    public void remove(long userId, Kind kind, TargetType type, long targetId) {
        int deleted = markMapper.delete(kind.table, userId, type.code, targetId);
        if (deleted > 0) {
            markMapper.bumpTargetCount(type.table, kind.countColumn, targetId, -1);
            evictCatalog();
        }
    }

    public boolean has(long userId, Kind kind, TargetType type, long targetId) {
        return markMapper.exists(kind.table, userId, type.code, targetId) > 0;
    }

    public PageResult<UserTargetItem> list(long userId, Kind kind, TargetType type, int page, int size) {
        List<Long> targetIds = markMapper.targetIds(kind.table, userId, type.code);
        List<UserTargetItem> items = switch (type) {
            case PRODUCT -> products(targetIds);
            case LOOK -> looks(targetIds);
        };
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return new PageResult<>(items.subList(from, to), items.size(), page, size);
    }

    private List<UserTargetItem> products(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productMapper.selectBatchIds(ids).stream()
                .filter(product -> ON.equals(product.getStatus()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Long> visibleIds = ids.stream().filter(byId::containsKey).toList();
        Map<Long, Integer> minPrice = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                        .in(Sku::getProductId, visibleIds)
                        .eq(Sku::getStatus, ON))
                .stream()
                .collect(Collectors.toMap(Sku::getProductId, sku -> nz(sku.getPriceCent()), Math::min));
        return visibleIds.stream().map(id -> {
            Product product = byId.get(id);
            return new UserTargetItem(TargetType.PRODUCT.code, id, product.getTitle(),
                    product.getSubtitle(), product.getCoverUrl(), nz(product.getCoverWidth()),
                    nz(product.getCoverHeight()), minPrice.get(id), nz(product.getLikeCount()));
        }).toList();
    }

    private List<UserTargetItem> looks(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Look> byId = lookMapper.selectBatchIds(ids).stream()
                .filter(look -> ON.equals(look.getStatus()))
                .collect(Collectors.toMap(Look::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(look -> new UserTargetItem(TargetType.LOOK.code, look.getId(), look.getTitle(),
                        look.getDescription(), look.getCoverUrl(), nz(look.getCoverWidth()),
                        nz(look.getCoverHeight()), null, nz(look.getLikeCount())))
                .toList();
    }

    private void requireTarget(TargetType type, long targetId) {
        String status = switch (type) {
            case PRODUCT -> {
                Product product = productMapper.selectById(targetId);
                yield product == null ? null : product.getStatus();
            }
            case LOOK -> {
                Look look = lookMapper.selectById(targetId);
                yield look == null ? null : look.getStatus();
            }
        };
        if (!ON.equals(status)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    private void evictCatalog() {
        cache.evictByPrefix(CacheKeys.PRODUCT_PREFIX);
        cache.evictByPrefix(CacheKeys.LIST_PREFIX);
        cache.evictByPrefix(CacheKeys.HOME_PREFIX);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
