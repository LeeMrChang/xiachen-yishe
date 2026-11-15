package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiachen.yishe.admin.dto.LookAdminDetail;
import com.xiachen.yishe.admin.dto.LookItemsRequest;
import com.xiachen.yishe.admin.dto.LookRow;
import com.xiachen.yishe.admin.dto.LookSaveRequest;
import com.xiachen.yishe.admin.support.CacheEvictor;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.PageResult;
import com.xiachen.yishe.domain.entity.Look;
import com.xiachen.yishe.domain.entity.LookItem;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.mapper.LookItemMapper;
import com.xiachen.yishe.domain.mapper.LookMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LookAdminService {

    private static final String ON = "ON";
    private static final String OFF = "OFF";
    private static final String DRAFT = "DRAFT";

    private final LookMapper lookMapper;
    private final LookItemMapper lookItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CacheEvictor cacheEvictor;

    public LookAdminService(LookMapper lookMapper, LookItemMapper lookItemMapper,
                            ProductMapper productMapper, SkuMapper skuMapper,
                            CacheEvictor cacheEvictor) {
        this.lookMapper = lookMapper;
        this.lookItemMapper = lookItemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.cacheEvictor = cacheEvictor;
    }

    public PageResult<LookRow> list(String keyword, String status, int page, int size) {
        var query = Wrappers.<Look>lambdaQuery()
                .orderByAsc(Look::getSortNo)
                .orderByAsc(Look::getId);
        if (hasText(keyword)) {
            query.and(w -> w.like(Look::getTitle, keyword).or().like(Look::getDescription, keyword));
        }
        if (hasText(status)) {
            query.eq(Look::getStatus, status);
        }
        IPage<Look> result = lookMapper.selectPage(new Page<>(page, size), query);
        List<Look> looks = result.getRecords();
        Map<Long, Long> itemCounts = itemCounts(looks.stream().map(Look::getId).toList());
        List<LookRow> rows = looks.stream()
                .map(look -> new LookRow(look.getId(), look.getTitle(), look.getDescription(),
                        look.getCoverUrl(), nz(look.getCoverWidth()), nz(look.getCoverHeight()),
                        look.getStatus(), nz(look.getSortNo()),
                        itemCounts.getOrDefault(look.getId(), 0L).intValue(), look.getUpdatedAt()))
                .toList();
        return new PageResult<>(rows, result.getTotal(), page, size);
    }

    public LookAdminDetail detail(Long id) {
        Look look = requireLook(id);
        return buildDetail(look);
    }

    @Transactional
    public LookAdminDetail create(LookSaveRequest request) {
        Look look = new Look();
        look.setStatus(DRAFT);
        look.setLikeCount(0);
        look.setFavCount(0);
        applyFields(look, request);
        lookMapper.insert(look);
        cacheEvictor.evictCatalog();
        return detail(look.getId());
    }

    @Transactional
    public LookAdminDetail update(Long id, LookSaveRequest request) {
        Look look = requireLook(id);
        applyFields(look, request);
        lookMapper.updateById(look);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    /** 全量保存造型单品：顺序即排序，重复 ID 去重 */
    @Transactional
    public LookAdminDetail saveItems(Long id, LookItemsRequest request) {
        requireLook(id);
        List<Long> productIds = new ArrayList<>(new LinkedHashSet<>(request.productIds()));
        List<Product> products = productMapper.selectBatchIds(productIds);
        if (products.size() != productIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "部分商品不存在，请刷新后重试");
        }
        lookItemMapper.delete(Wrappers.<LookItem>lambdaQuery().eq(LookItem::getLookId, id));
        int index = 0;
        for (Long productId : productIds) {
            LookItem item = new LookItem();
            item.setLookId(id);
            item.setProductId(productId);
            item.setSortNo(index++);
            lookItemMapper.insert(item);
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public LookAdminDetail publish(Long id) {
        Look look = requireLook(id);
        if (!hasText(look.getCoverUrl())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发布前请先上传造型封面");
        }
        if (lookItemMapper.selectCount(Wrappers.<LookItem>lambdaQuery().eq(LookItem::getLookId, id)) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发布前请至少添加一件单品");
        }
        look.setStatus(ON);
        lookMapper.updateById(look);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public LookAdminDetail offline(Long id) {
        Look look = requireLook(id);
        look.setStatus(OFF);
        lookMapper.updateById(look);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    private LookAdminDetail buildDetail(Look look) {
        List<LookItem> items = lookItemMapper.selectList(Wrappers.<LookItem>lambdaQuery()
                .eq(LookItem::getLookId, look.getId())
                .orderByAsc(LookItem::getSortNo)
                .orderByAsc(LookItem::getId));
        Set<Long> productIds = items.stream().map(LookItem::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Integer> minPrices = minPrices(productIds);
        List<LookAdminDetail.Item> views = new ArrayList<>();
        for (LookItem item : items) {
            Product product = products.get(item.getProductId());
            if (product == null) {
                continue;
            }
            views.add(new LookAdminDetail.Item(product.getId(), product.getTitle(), product.getCoverUrl(),
                    minPrices.getOrDefault(product.getId(), 0), product.getStatus(), nz(item.getSortNo())));
        }
        return new LookAdminDetail(look.getId(), look.getTitle(), look.getDescription(), look.getCoverUrl(),
                nz(look.getCoverWidth()), nz(look.getCoverHeight()), look.getStatus(), nz(look.getSortNo()),
                look.getUpdatedAt(), views);
    }

    private Map<Long, Integer> minPrices(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                        .in(Sku::getProductId, productIds)
                        .eq(Sku::getStatus, ON))
                .stream()
                .collect(Collectors.toMap(Sku::getProductId, sku -> nz(sku.getPriceCent()),
                        Math::min));
    }

    private Map<Long, Long> itemCounts(List<Long> lookIds) {
        if (lookIds.isEmpty()) {
            return Map.of();
        }
        return lookItemMapper.selectList(Wrappers.<LookItem>lambdaQuery()
                        .in(LookItem::getLookId, lookIds))
                .stream()
                .collect(Collectors.groupingBy(LookItem::getLookId, Collectors.counting()));
    }

    private Look requireLook(Long id) {
        Look look = lookMapper.selectById(id);
        if (look == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return look;
    }

    private static void applyFields(Look look, LookSaveRequest request) {
        look.setTitle(request.title().trim());
        look.setDescription(request.description() == null ? "" : request.description().trim());
        look.setCoverUrl(request.coverUrl() == null ? "" : request.coverUrl().trim());
        look.setCoverWidth(nz(request.coverWidth()));
        look.setCoverHeight(nz(request.coverHeight()));
        look.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
