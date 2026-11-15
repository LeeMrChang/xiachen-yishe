package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.admin.dto.InventoryMatrix;
import com.xiachen.yishe.admin.dto.InventoryUpsertRequest;
import com.xiachen.yishe.admin.support.CacheEvictor;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.domain.entity.Inventory;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.entity.Store;
import com.xiachen.yishe.domain.mapper.InventoryMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventoryAdminService {

    private static final String ON = "ON";

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final StoreMapper storeMapper;
    private final InventoryMapper inventoryMapper;
    private final CacheEvictor cacheEvictor;

    public InventoryAdminService(ProductMapper productMapper, SkuMapper skuMapper, StoreMapper storeMapper,
                                 InventoryMapper inventoryMapper, CacheEvictor cacheEvictor) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.storeMapper = storeMapper;
        this.inventoryMapper = inventoryMapper;
        this.cacheEvictor = cacheEvictor;
    }

    public InventoryMatrix matrix(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, productId)
                .orderByAsc(Sku::getSortNo)
                .orderByAsc(Sku::getId));
        List<Store> stores = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                .eq(Store::getStatus, ON)
                .orderByAsc(Store::getSortNo));
        Map<String, Inventory> byStoreSku = new HashMap<>();
        if (!skus.isEmpty()) {
            inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                            .in(Inventory::getSkuId, skus.stream().map(Sku::getId).toList()))
                    .forEach(i -> byStoreSku.put(key(i.getStoreId(), i.getSkuId()), i));
        }
        List<InventoryMatrix.Cell> cells = skus.stream()
                .flatMap(sku -> stores.stream().map(store -> {
                    Inventory row = byStoreSku.get(key(store.getId(), sku.getId()));
                    return new InventoryMatrix.Cell(sku.getId(), store.getId(),
                            row == null ? 0 : nz(row.getQuantity()),
                            row == null || !Boolean.FALSE.equals(row.getOnSale()));
                }))
                .toList();
        return new InventoryMatrix(product.getId(), product.getTitle(),
                skus.stream().map(s -> new InventoryMatrix.SkuCol(s.getId(), s.getColor(), s.getSize(),
                        s.getStatus())).toList(),
                stores.stream().map(s -> new InventoryMatrix.StoreCol(s.getId(), s.getName())).toList(),
                cells);
    }

    @Transactional
    public int upsert(InventoryUpsertRequest request) {
        Set<Long> skuIds = request.items().stream().map(InventoryUpsertRequest.Item::skuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> storeIds = request.items().stream().map(InventoryUpsertRequest.Item::storeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> knownSkus = skuMapper.selectBatchIds(skuIds).stream().map(Sku::getId).collect(Collectors.toSet());
        Set<Long> knownStores = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .in(Store::getId, storeIds).eq(Store::getStatus, ON))
                .stream().map(Store::getId).collect(Collectors.toSet());
        for (InventoryUpsertRequest.Item item : request.items()) {
            if (!knownSkus.contains(item.skuId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "SKU 不存在：" + item.skuId());
            }
            if (!knownStores.contains(item.storeId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "门店不存在或已停用：" + item.storeId());
            }
        }

        Map<String, Inventory> existing = inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                        .in(Inventory::getSkuId, skuIds)
                        .in(Inventory::getStoreId, storeIds))
                .stream().collect(Collectors.toMap(i -> key(i.getStoreId(), i.getSkuId()), i -> i, (a, b) -> a));

        for (InventoryUpsertRequest.Item item : request.items()) {
            String key = key(item.storeId(), item.skuId());
            boolean onSale = item.onSale() == null || item.onSale();
            Inventory row = existing.get(key);
            if (row == null) {
                row = new Inventory();
                row.setStoreId(item.storeId());
                row.setSkuId(item.skuId());
                row.setQuantity(item.quantity());
                row.setOnSale(onSale);
                inventoryMapper.insert(row);
            } else {
                row.setQuantity(item.quantity());
                row.setOnSale(onSale);
                inventoryMapper.updateById(row);
            }
        }
        cacheEvictor.evictCatalog();
        return request.items().size();
    }

    private static String key(Long storeId, Long skuId) {
        return storeId + ":" + skuId;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
