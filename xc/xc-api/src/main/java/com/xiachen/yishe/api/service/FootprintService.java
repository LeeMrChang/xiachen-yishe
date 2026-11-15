package com.xiachen.yishe.api.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.api.dto.FootprintItem;
import com.xiachen.yishe.domain.entity.Inventory;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.entity.Store;
import com.xiachen.yishe.domain.entity.UserFootprint;
import com.xiachen.yishe.domain.mapper.InventoryMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import com.xiachen.yishe.domain.mapper.UserFootprintMapper;
import com.xiachen.yishe.domain.service.StockStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FootprintService {

    private static final String ON = "ON";
    private static final Duration KEEP = Duration.ofDays(30);
    private static final int MAX_LIMIT = 100;

    private final UserFootprintMapper footprintMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final InventoryMapper inventoryMapper;
    private final StoreMapper storeMapper;

    public FootprintService(UserFootprintMapper footprintMapper, ProductMapper productMapper,
                            SkuMapper skuMapper, InventoryMapper inventoryMapper, StoreMapper storeMapper) {
        this.footprintMapper = footprintMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.inventoryMapper = inventoryMapper;
        this.storeMapper = storeMapper;
    }

    public void record(long userId, long productId, Long storeId) {
        footprintMapper.upsert(userId, productId, storeId == null ? 0 : storeId);
    }

    public List<FootprintItem> list(long userId, int limit) {
        LocalDateTime since = LocalDateTime.now().minus(KEEP);
        footprintMapper.deleteBefore(userId, since);
        List<UserFootprint> rows = footprintMapper.recent(userId, since, Math.min(Math.max(limit, 1), MAX_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> productIds = rows.stream().map(UserFootprint::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .filter(product -> ON.equals(product.getStatus()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.isEmpty()) {
            return List.of();
        }

        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .in(Sku::getProductId, products.keySet())
                .eq(Sku::getStatus, ON));
        Map<Long, List<Long>> skuIdsByProduct = skus.stream()
                .collect(Collectors.groupingBy(Sku::getProductId,
                        Collectors.mapping(Sku::getId, Collectors.toList())));
        Map<Long, Integer> minPrice = skus.stream()
                .collect(Collectors.toMap(Sku::getProductId, sku -> nz(sku.getPriceCent()), Math::min));

        List<Long> allSkuIds = skuIdsByProduct.values().stream().flatMap(List::stream).toList();
        Map<Long, Map<Long, Integer>> quantityByStoreSku = new HashMap<>();
        if (!allSkuIds.isEmpty()) {
            inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                            .in(Inventory::getSkuId, allSkuIds)
                            .eq(Inventory::getOnSale, true))
                    .forEach(row -> quantityByStoreSku
                            .computeIfAbsent(row.getStoreId(), key -> new HashMap<>())
                            .merge(row.getSkuId(), nz(row.getQuantity()), Integer::sum));
        }

        Set<Long> storeIds = rows.stream().map(UserFootprint::getStoreId)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Map<Long, Store> stores = storeIds.isEmpty()
                ? Map.of()
                : storeMapper.selectBatchIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));

        return rows.stream()
                .map(row -> toItem(row, products.get(row.getProductId()), skuIdsByProduct, minPrice,
                        quantityByStoreSku, stores))
                .filter(Objects::nonNull)
                .toList();
    }

    public void clear(long userId) {
        footprintMapper.deleteAll(userId);
    }

    private FootprintItem toItem(UserFootprint row, Product product, Map<Long, List<Long>> skuIdsByProduct,
                                 Map<Long, Integer> minPrice, Map<Long, Map<Long, Integer>> quantityByStoreSku,
                                 Map<Long, Store> stores) {
        if (product == null) {
            return null;
        }
        long storeId = row.getStoreId() == null ? 0 : row.getStoreId();
        Store store = storeId > 0 ? stores.get(storeId) : null;
        int quantity = 0;
        if (storeId > 0) {
            Map<Long, Integer> bySku = quantityByStoreSku.getOrDefault(storeId, Map.of());
            quantity = skuIdsByProduct.getOrDefault(product.getId(), List.of()).stream()
                    .mapToInt(skuId -> bySku.getOrDefault(skuId, 0)).sum();
        }
        return new FootprintItem(product.getId(), product.getTitle(), product.getCoverUrl(),
                nz(product.getCoverWidth()), nz(product.getCoverHeight()), minPrice.get(product.getId()),
                storeId, store == null ? "" : store.getName(),
                storeId > 0 ? StockStatus.of(quantity).name() : null, quantity, row.getViewedAt());
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
