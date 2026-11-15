package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiachen.yishe.admin.dto.ImageBindRequest;
import com.xiachen.yishe.admin.dto.ProductAdminDetail;
import com.xiachen.yishe.admin.dto.ProductRow;
import com.xiachen.yishe.admin.dto.ProductSaveRequest;
import com.xiachen.yishe.admin.dto.SkuBatchRequest;
import com.xiachen.yishe.admin.support.CacheEvictor;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.PageResult;
import com.xiachen.yishe.domain.entity.Inventory;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.ProductImage;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.mapper.InventoryMapper;
import com.xiachen.yishe.domain.mapper.ProductImageMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProductAdminService {

    private static final String ON = "ON";
    private static final String OFF = "OFF";
    private static final String DRAFT = "DRAFT";

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final SkuMapper skuMapper;
    private final InventoryMapper inventoryMapper;
    private final CacheEvictor cacheEvictor;

    public ProductAdminService(ProductMapper productMapper,
                               ProductImageMapper productImageMapper,
                               SkuMapper skuMapper,
                               InventoryMapper inventoryMapper,
                               CacheEvictor cacheEvictor) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.skuMapper = skuMapper;
        this.inventoryMapper = inventoryMapper;
        this.cacheEvictor = cacheEvictor;
    }

    public PageResult<ProductRow> list(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<Product> query = Wrappers.<Product>lambdaQuery()
                .orderByDesc(Product::getUpdatedAt);
        if (hasText(keyword)) {
            query.and(w -> w.like(Product::getTitle, keyword).or().like(Product::getSubtitle, keyword));
        }
        if (hasText(status)) {
            query.eq(Product::getStatus, status);
        }
        IPage<Product> result = productMapper.selectPage(new Page<>(page, pageSize), query);
        List<Product> products = result.getRecords();
        if (products.isEmpty()) {
            return new PageResult<>(List.of(), result.getTotal(), page, pageSize);
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .in(Sku::getProductId, ids)
                .eq(Sku::getStatus, ON));
        Map<Long, List<Sku>> skusByProduct = skus.stream().collect(Collectors.groupingBy(Sku::getProductId));
        Map<Long, Integer> quantityBySku = new LinkedHashMap<>();
        if (!skus.isEmpty()) {
            inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                            .in(Inventory::getSkuId, skus.stream().map(Sku::getId).toList()))
                    .forEach(i -> quantityBySku.merge(i.getSkuId(), nz(i.getQuantity()), Integer::sum));
        }
        List<ProductRow> rows = products.stream().map(p -> {
            List<Sku> productSkus = skusByProduct.getOrDefault(p.getId(), List.of());
            int price = productSkus.stream().mapToInt(s -> nz(s.getPriceCent())).min().orElse(0);
            int quantity = productSkus.stream().mapToInt(s -> quantityBySku.getOrDefault(s.getId(), 0)).sum();
            return new ProductRow(p.getId(), p.getTitle(), p.getCoverUrl(), p.getCategory(), p.getStatus(),
                    price, quantity, productSkus.size(), p.getOnlineAt(), p.getUpdatedAt());
        }).toList();
        return new PageResult<>(rows, result.getTotal(), page, pageSize);
    }

    public ProductAdminDetail detail(Long id) {
        Product product = requireProduct(id);
        List<ProductImage> images = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id)
                .orderByAsc(ProductImage::getSortNo)
                .orderByAsc(ProductImage::getId));
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, id)
                .orderByAsc(Sku::getSortNo)
                .orderByAsc(Sku::getId));
        Map<Long, Integer> quantityBySku = new LinkedHashMap<>();
        if (!skus.isEmpty()) {
            inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                            .in(Inventory::getSkuId, skus.stream().map(Sku::getId).toList()))
                    .forEach(i -> quantityBySku.merge(i.getSkuId(), nz(i.getQuantity()), Integer::sum));
        }
        return toDetail(product, images, skus, quantityBySku);
    }

    @Transactional
    public ProductAdminDetail create(ProductSaveRequest request) {
        Product product = new Product();
        product.setStatus(DRAFT);
        product.setCoverUrl("");
        product.setCoverWidth(0);
        product.setCoverHeight(0);
        product.setLikeCount(0);
        product.setFavCount(0);
        applyFields(product, request);
        productMapper.insert(product);
        cacheEvictor.evictCatalog();
        return detail(product.getId());
    }

    @Transactional
    public ProductAdminDetail update(Long id, ProductSaveRequest request) {
        Product product = requireProduct(id);
        applyFields(product, request);
        productMapper.updateById(product);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public ProductAdminDetail publish(Long id) {
        Product product = requireProduct(id);
        long imageCount = productImageMapper.selectCount(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id));
        if (imageCount == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发布前请至少上传 1 张商品图");
        }
        boolean hasSellableSku = skuMapper.selectCount(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, id)
                .eq(Sku::getStatus, ON)
                .gt(Sku::getPriceCent, 0)) > 0;
        if (!hasSellableSku) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发布前请先维护至少一个带售价的 SKU");
        }
        product.setStatus(ON);
        if (product.getOnlineAt() == null) {
            product.setOnlineAt(LocalDateTime.now());
        }
        productMapper.updateById(product);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public ProductAdminDetail offline(Long id) {
        Product product = requireProduct(id);
        product.setStatus(OFF);
        productMapper.updateById(product);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    /** 全量保存 SKU：新组合插入、已有组合更新，未提交的置 OFF（保留库存历史） */
    @Transactional
    public ProductAdminDetail saveSkus(Long id, SkuBatchRequest request) {
        requireProduct(id);
        Map<String, SkuBatchRequest.Item> incoming = new LinkedHashMap<>();
        for (SkuBatchRequest.Item item : request.items()) {
            String color = item.color().trim();
            String size = item.size().trim();
            if (color.isEmpty() || size.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "颜色和尺码不能为空");
            }
            if (incoming.putIfAbsent(key(color, size), item) != null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "颜色「" + color + "」尺码「" + size + "」重复");
            }
        }
        List<Sku> existing = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, id)
                .orderByAsc(Sku::getSortNo));
        Map<String, Sku> byKey = existing.stream().collect(Collectors.toMap(
                s -> key(s.getColor(), s.getSize()), s -> s, (a, b) -> a));

        int index = 0;
        for (Map.Entry<String, SkuBatchRequest.Item> entry : incoming.entrySet()) {
            SkuBatchRequest.Item item = entry.getValue();
            int origin = item.originPriceCent() == null ? 0 : item.originPriceCent();
            Sku sku = byKey.get(entry.getKey());
            if (sku == null) {
                sku = new Sku();
                sku.setProductId(id);
                sku.setColor(item.color().trim());
                sku.setSize(item.size().trim());
                sku.setPriceCent(item.priceCent());
                sku.setOriginPriceCent(origin);
                sku.setSortNo(index);
                sku.setStatus(ON);
                skuMapper.insert(sku);
            } else {
                sku.setPriceCent(item.priceCent());
                sku.setOriginPriceCent(origin);
                sku.setSortNo(index);
                sku.setStatus(ON);
                skuMapper.updateById(sku);
            }
            index++;
        }
        for (Sku sku : existing) {
            if (!incoming.containsKey(key(sku.getColor(), sku.getSize())) && !OFF.equals(sku.getStatus())) {
                sku.setStatus(OFF);
                skuMapper.updateById(sku);
            }
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public ProductAdminDetail bindImage(Long id, ImageBindRequest request) {
        Product product = requireProduct(id);
        List<ProductImage> images = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id));
        int nextSort = images.stream().mapToInt(i -> nz(i.getSortNo())).max().orElse(-1) + 1;
        ProductImage image = new ProductImage();
        image.setProductId(id);
        image.setUrl(request.url().trim());
        image.setWidth(nz(request.width()));
        image.setHeight(nz(request.height()));
        image.setSortNo(nextSort);
        productImageMapper.insert(image);
        if (!hasText(product.getCoverUrl())) {
            applyCover(product, image);
            productMapper.updateById(product);
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public ProductAdminDetail unbindImage(Long id, Long imageId) {
        Product product = requireProduct(id);
        ProductImage image = productImageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.getProductId(), id)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        productImageMapper.deleteById(imageId);
        if (Objects.equals(product.getCoverUrl(), image.getUrl())) {
            List<ProductImage> rest = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                    .eq(ProductImage::getProductId, id)
                    .orderByAsc(ProductImage::getSortNo)
                    .orderByAsc(ProductImage::getId));
            if (rest.isEmpty()) {
                product.setCoverUrl("");
                product.setCoverWidth(0);
                product.setCoverHeight(0);
            } else {
                applyCover(product, rest.get(0));
            }
            productMapper.updateById(product);
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public ProductAdminDetail setCover(Long id, Long imageId) {
        Product product = requireProduct(id);
        List<ProductImage> images = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id)
                .orderByAsc(ProductImage::getSortNo)
                .orderByAsc(ProductImage::getId));
        ProductImage cover = images.stream()
                .filter(i -> Objects.equals(i.getId(), imageId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        applyCover(product, cover);
        productMapper.updateById(product);
        List<ProductImage> ordered = new ArrayList<>(images);
        ordered.sort(Comparator.comparing((ProductImage i) -> !Objects.equals(i.getId(), imageId))
                .thenComparing(i -> nz(i.getSortNo())));
        int index = 0;
        for (ProductImage image : ordered) {
            if (!Objects.equals(image.getSortNo(), index)) {
                image.setSortNo(index);
                productImageMapper.updateById(image);
            }
            index++;
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return product;
    }

    private static void applyFields(Product product, ProductSaveRequest request) {
        product.setTitle(request.title().trim());
        product.setSubtitle(blankToEmpty(request.subtitle()));
        product.setCategory(blankToEmpty(request.category()));
        product.setSeason(blankToEmpty(request.season()));
        product.setMaterial(blankToEmpty(request.material()));
        product.setDescription(request.description());
        product.setSortWeight(request.sortWeight() == null ? 0 : request.sortWeight());
    }

    private static void applyCover(Product product, ProductImage image) {
        product.setCoverUrl(image.getUrl());
        product.setCoverWidth(nz(image.getWidth()));
        product.setCoverHeight(nz(image.getHeight()));
    }

    private static ProductAdminDetail toDetail(Product product, List<ProductImage> images, List<Sku> skus,
                                               Map<Long, Integer> quantityBySku) {
        List<ProductAdminDetail.ImageItem> imageItems = images.stream()
                .map(i -> new ProductAdminDetail.ImageItem(i.getId(), i.getUrl(),
                        nz(i.getWidth()), nz(i.getHeight()), nz(i.getSortNo())))
                .toList();
        List<ProductAdminDetail.SkuItem> skuItems = skus.stream()
                .map(s -> new ProductAdminDetail.SkuItem(s.getId(), s.getColor(), s.getSize(),
                        nz(s.getPriceCent()), nz(s.getOriginPriceCent()), nz(s.getSortNo()),
                        s.getStatus(), quantityBySku.getOrDefault(s.getId(), 0)))
                .toList();
        return new ProductAdminDetail(product.getId(), product.getTitle(), product.getSubtitle(),
                product.getCategory(), product.getSeason(), product.getMaterial(), product.getDescription(),
                product.getCoverUrl(), nz(product.getCoverWidth()), nz(product.getCoverHeight()),
                product.getStatus(), nz(product.getSortWeight()), nz(product.getLikeCount()),
                nz(product.getFavCount()), product.getOnlineAt(), product.getUpdatedAt(),
                imageItems, skuItems);
    }

    private static String key(String color, String size) {
        return color + "\u0001" + size;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
