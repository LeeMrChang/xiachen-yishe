package com.xiachen.yishe.api.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.api.dto.TryonGroup;
import com.xiachen.yishe.api.dto.TryonItemView;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.domain.entity.Inventory;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.entity.Store;
import com.xiachen.yishe.domain.entity.TryonItem;
import com.xiachen.yishe.domain.mapper.InventoryMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import com.xiachen.yishe.domain.mapper.TryonItemMapper;
import com.xiachen.yishe.domain.service.StockStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TryonService {

    private static final String ON = "ON";

    private final TryonItemMapper tryonItemMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final StoreMapper storeMapper;
    private final InventoryMapper inventoryMapper;

    public TryonService(TryonItemMapper tryonItemMapper, SkuMapper skuMapper, ProductMapper productMapper,
                        StoreMapper storeMapper, InventoryMapper inventoryMapper) {
        this.tryonItemMapper = tryonItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.storeMapper = storeMapper;
        this.inventoryMapper = inventoryMapper;
    }

    public void add(long userId, long skuId, long storeId) {
        requireSku(skuId);
        requireStore(storeId);
        tryonItemMapper.upsert(userId, skuId, storeId);
    }

    public void move(long userId, long id, long storeId) {
        requireStore(storeId);
        TryonItem item = tryonItemMapper.selectById(id);
        if (item == null || !Long.valueOf(userId).equals(item.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (Long.valueOf(storeId).equals(item.getStoreId())) {
            return;
        }
        try {
            tryonItemMapper.moveOwned(id, userId, storeId);
        } catch (DuplicateKeyException e) {
            // 目标门店已有同一 SKU，合并成一条
            tryonItemMapper.deleteOwned(id, userId);
        }
    }

    public void remove(long userId, long id) {
        tryonItemMapper.deleteOwned(id, userId);
    }

    public boolean hasProduct(long userId, long productId, Long storeId) {
        if (storeId == null) {
            return false;
        }
        return tryonItemMapper.countProduct(userId, productId, storeId) > 0;
    }

    public List<TryonGroup> list(long userId) {
        List<TryonItem> items = tryonItemMapper.selectList(Wrappers.<TryonItem>lambdaQuery()
                .eq(TryonItem::getUserId, userId)
                .orderByDesc(TryonItem::getCreatedAt)
                .orderByDesc(TryonItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }

        Set<Long> skuIds = items.stream().map(TryonItem::getSkuId).collect(Collectors.toSet());
        Map<Long, Sku> skus = skuMapper.selectBatchIds(skuIds).stream()
                .filter(sku -> ON.equals(sku.getStatus()))
                .collect(Collectors.toMap(Sku::getId, Function.identity()));
        Set<Long> productIds = skus.values().stream().map(Sku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .filter(product -> ON.equals(product.getStatus()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<Long> storeIds = items.stream().map(TryonItem::getStoreId).collect(Collectors.toSet());
        Map<Long, Store> stores = storeMapper.selectBatchIds(storeIds).stream()
                .filter(store -> ON.equals(store.getStatus()))
                .collect(Collectors.toMap(Store::getId, Function.identity()));

        Map<Long, Map<Long, Integer>> quantityByStoreSku = new HashMap<>();
        if (!skuIds.isEmpty()) {
            inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                            .in(Inventory::getStoreId, storeIds)
                            .in(Inventory::getSkuId, skuIds)
                            .eq(Inventory::getOnSale, true))
                    .forEach(row -> quantityByStoreSku
                            .computeIfAbsent(row.getStoreId(), key -> new HashMap<>())
                            .merge(row.getSkuId(), nz(row.getQuantity()), Integer::sum));
        }

        Map<Long, List<TryonItemView>> byStore = new LinkedHashMap<>();
        for (TryonItem item : items) {
            Sku sku = skus.get(item.getSkuId());
            Store store = stores.get(item.getStoreId());
            if (sku == null || store == null) {
                continue;
            }
            Product product = products.get(sku.getProductId());
            if (product == null) {
                continue;
            }
            int quantity = quantityByStoreSku
                    .getOrDefault(item.getStoreId(), Map.of())
                    .getOrDefault(item.getSkuId(), 0);
            TryonItemView view = new TryonItemView(item.getId(), sku.getId(), product.getId(),
                    product.getTitle(), product.getCoverUrl(), nz(product.getCoverWidth()),
                    nz(product.getCoverHeight()), sku.getColor(), sku.getSize(),
                    nz(sku.getPriceCent()), quantity, StockStatus.of(quantity).name());
            byStore.computeIfAbsent(store.getId(), key -> new ArrayList<>()).add(view);
        }

        return stores.values().stream()
                .sorted(Comparator.comparing(Store::getSortNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .filter(store -> byStore.containsKey(store.getId()))
                .map(store -> new TryonGroup(store.getId(), store.getName(), store.getAddress(),
                        byStore.get(store.getId())))
                .toList();
    }

    private Sku requireSku(long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null || !ON.equals(sku.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || !ON.equals(product.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return sku;
    }

    private void requireStore(long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null || !ON.equals(store.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "门店暂不可选，请换一家");
        }
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
