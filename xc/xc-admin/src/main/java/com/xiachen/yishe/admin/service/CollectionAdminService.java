package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.admin.dto.CollectionDetail;
import com.xiachen.yishe.admin.dto.CollectionItemsRequest;
import com.xiachen.yishe.admin.dto.CollectionRow;
import com.xiachen.yishe.admin.dto.CollectionSaveRequest;
import com.xiachen.yishe.admin.support.CacheEvictor;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.domain.entity.Collection;
import com.xiachen.yishe.domain.entity.CollectionProduct;
import com.xiachen.yishe.domain.entity.HomeModule;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.mapper.CollectionMapper;
import com.xiachen.yishe.domain.mapper.CollectionProductMapper;
import com.xiachen.yishe.domain.mapper.HomeModuleMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系列维护：系列本身的增改、上下线，以及系列内的商品成员。
 *
 * <p>系列是首页「系列」模块的数据源；此前只有列表查询，运营无法创建系列，
 * 导致首页主题的系列模块在生产环境无法配置。</p>
 */
@Service
public class CollectionAdminService {

    private static final String ON = "ON";
    private static final String OFF = "OFF";
    private static final String DRAFT = "DRAFT";

    private final CollectionMapper collectionMapper;
    private final CollectionProductMapper collectionProductMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final HomeModuleMapper homeModuleMapper;
    private final CacheEvictor cacheEvictor;

    public CollectionAdminService(CollectionMapper collectionMapper,
                                  CollectionProductMapper collectionProductMapper,
                                  ProductMapper productMapper,
                                  SkuMapper skuMapper,
                                  HomeModuleMapper homeModuleMapper,
                                  CacheEvictor cacheEvictor) {
        this.collectionMapper = collectionMapper;
        this.collectionProductMapper = collectionProductMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.homeModuleMapper = homeModuleMapper;
        this.cacheEvictor = cacheEvictor;
    }

    public List<CollectionRow> list(String keyword, String status) {
        var query = Wrappers.<Collection>lambdaQuery()
                .orderByAsc(Collection::getSortNo)
                .orderByAsc(Collection::getId);
        if (hasText(keyword)) {
            query.and(w -> w.like(Collection::getName, keyword).or().like(Collection::getStory, keyword));
        }
        if (hasText(status)) {
            query.eq(Collection::getStatus, status);
        }
        List<Collection> collections = collectionMapper.selectList(query);
        Map<Long, Long> counts = productCounts(collections.stream().map(Collection::getId).toList());
        return collections.stream()
                .map(c -> new CollectionRow(c.getId(), c.getName(), c.getCoverUrl(), c.getStory(),
                        c.getPublishAt(), c.getStatus(), nz(c.getSortNo()),
                        counts.getOrDefault(c.getId(), 0L).intValue(), c.getUpdatedAt()))
                .toList();
    }

    public CollectionDetail detail(Long id) {
        return buildDetail(requireCollection(id));
    }

    @Transactional
    public CollectionDetail create(CollectionSaveRequest request) {
        String name = request.name().trim();
        requireUniqueName(name, null);
        Collection collection = new Collection();
        applyFields(collection, request, name);
        if (!hasText(collection.getStatus())) {
            collection.setStatus(DRAFT);
        }
        collectionMapper.insert(collection);
        cacheEvictor.evictCatalog();
        return detail(collection.getId());
    }

    @Transactional
    public CollectionDetail update(Long id, CollectionSaveRequest request) {
        Collection collection = requireCollection(id);
        String name = request.name().trim();
        requireUniqueName(name, id);
        applyFields(collection, request, name);
        if (!hasText(collection.getStatus())) {
            collection.setStatus(DRAFT);
        }
        collectionMapper.updateById(collection);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    /** 全量保存系列商品：数组顺序即展示顺序，重复 ID 去重，空数组表示清空 */
    @Transactional
    public CollectionDetail saveItems(Long id, CollectionItemsRequest request) {
        requireCollection(id);
        List<Long> productIds = new ArrayList<>(new LinkedHashSet<>(request.productIds()));
        if (!productIds.isEmpty()) {
            List<Product> products = productMapper.selectBatchIds(productIds);
            if (products.size() != productIds.size()) {
                throw new BizException(ErrorCode.NOT_FOUND, "部分商品不存在，请刷新后重试");
            }
        }
        collectionProductMapper.delete(Wrappers.<CollectionProduct>lambdaQuery()
                .eq(CollectionProduct::getCollectionId, id));
        int index = 0;
        for (Long productId : productIds) {
            CollectionProduct link = new CollectionProduct();
            link.setCollectionId(id);
            link.setProductId(productId);
            link.setSortNo(index++);
            collectionProductMapper.insert(link);
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public CollectionDetail publish(Long id) {
        Collection collection = requireCollection(id);
        long members = collectionProductMapper.selectCount(Wrappers.<CollectionProduct>lambdaQuery()
                .eq(CollectionProduct::getCollectionId, id));
        if (members == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上线前请至少添加一件商品");
        }
        collection.setStatus(ON);
        if (collection.getPublishAt() == null) {
            collection.setPublishAt(LocalDateTime.now());
        }
        collectionMapper.updateById(collection);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public CollectionDetail offline(Long id) {
        Collection collection = requireCollection(id);
        collection.setStatus(OFF);
        collectionMapper.updateById(collection);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    /**
     * 删除系列。系列是分组而非内容，允许删除；但若被首页主题的系列模块引用则拒绝，
     * 避免主题里留下指向不存在系列的模块。
     */
    @Transactional
    public void delete(Long id) {
        requireCollection(id);
        Long references = homeModuleMapper.selectCount(Wrappers.<HomeModule>lambdaQuery()
                .eq(HomeModule::getType, "COLLECTION")
                .eq(HomeModule::getRefId, id));
        if (references != null && references > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "该系列正被 " + references + " 个首页主题使用，请先在主题里移除「系列」模块");
        }
        collectionProductMapper.delete(Wrappers.<CollectionProduct>lambdaQuery()
                .eq(CollectionProduct::getCollectionId, id));
        collectionMapper.deleteById(id);
        cacheEvictor.evictCatalog();
    }

    private CollectionDetail buildDetail(Collection collection) {
        List<CollectionProduct> links = collectionProductMapper.selectList(
                Wrappers.<CollectionProduct>lambdaQuery()
                        .eq(CollectionProduct::getCollectionId, collection.getId())
                        .orderByAsc(CollectionProduct::getSortNo)
                        .orderByAsc(CollectionProduct::getId));
        Set<Long> productIds = links.stream().map(CollectionProduct::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Integer> minPrices = minPrices(productIds);
        List<CollectionDetail.Item> views = new ArrayList<>();
        for (CollectionProduct link : links) {
            Product product = products.get(link.getProductId());
            if (product == null) {
                continue;
            }
            views.add(new CollectionDetail.Item(product.getId(), product.getTitle(), product.getCoverUrl(),
                    minPrices.getOrDefault(product.getId(), 0), product.getStatus(), nz(link.getSortNo())));
        }
        return new CollectionDetail(collection.getId(), collection.getName(), collection.getCoverUrl(),
                collection.getStory(), collection.getPublishAt(), collection.getStatus(),
                nz(collection.getSortNo()), collection.getUpdatedAt(), views);
    }

    private void requireUniqueName(String name, Long selfId) {
        Long exists = collectionMapper.selectCount(Wrappers.<Collection>lambdaQuery()
                .eq(Collection::getName, name)
                .ne(selfId != null, Collection::getId, selfId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "系列名称已存在：" + name);
        }
    }

    private Map<Long, Integer> minPrices(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                        .in(Sku::getProductId, productIds)
                        .eq(Sku::getStatus, ON))
                .stream()
                .collect(Collectors.toMap(Sku::getProductId, sku -> nz(sku.getPriceCent()), Math::min));
    }

    private Map<Long, Long> productCounts(List<Long> collectionIds) {
        if (collectionIds.isEmpty()) {
            return Map.of();
        }
        return collectionProductMapper.selectList(Wrappers.<CollectionProduct>lambdaQuery()
                        .in(CollectionProduct::getCollectionId, collectionIds))
                .stream()
                .collect(Collectors.groupingBy(CollectionProduct::getCollectionId, Collectors.counting()));
    }

    private Collection requireCollection(Long id) {
        Collection collection = collectionMapper.selectById(id);
        if (collection == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return collection;
    }

    private static void applyFields(Collection collection, CollectionSaveRequest request, String name) {
        collection.setName(name);
        collection.setCoverUrl(request.coverUrl() == null ? "" : request.coverUrl().trim());
        collection.setStory(request.story() == null ? "" : request.story().trim());
        collection.setPublishAt(request.publishAt());
        collection.setStatus(request.status() == null ? null : request.status());
        collection.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
