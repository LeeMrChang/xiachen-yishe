package com.xiachen.yishe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.domain.entity.Collection;
import com.xiachen.yishe.domain.entity.CollectionProduct;
import com.xiachen.yishe.domain.entity.HomeModule;
import com.xiachen.yishe.domain.entity.HomeTheme;
import com.xiachen.yishe.domain.entity.Inventory;
import com.xiachen.yishe.domain.entity.Look;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.ProductImage;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.entity.Store;
import com.xiachen.yishe.domain.mapper.CollectionMapper;
import com.xiachen.yishe.domain.mapper.CollectionProductMapper;
import com.xiachen.yishe.domain.mapper.HomeModuleMapper;
import com.xiachen.yishe.domain.mapper.HomeThemeMapper;
import com.xiachen.yishe.domain.mapper.InventoryMapper;
import com.xiachen.yishe.domain.mapper.LookMapper;
import com.xiachen.yishe.domain.mapper.ProductImageMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import com.xiachen.yishe.domain.view.HomeView;
import com.xiachen.yishe.domain.view.LookCard;
import com.xiachen.yishe.domain.view.ProductCard;
import com.xiachen.yishe.domain.view.ProductDetailView;
import com.xiachen.yishe.domain.view.ProductFilter;
import com.xiachen.yishe.domain.view.StoreView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private static final String ON = "ON";

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final SkuMapper skuMapper;
    private final InventoryMapper inventoryMapper;
    private final StoreMapper storeMapper;
    private final CollectionMapper collectionMapper;
    private final CollectionProductMapper collectionProductMapper;
    private final LookMapper lookMapper;
    private final HomeThemeMapper homeThemeMapper;
    private final HomeModuleMapper homeModuleMapper;

    public CatalogService(ProductMapper productMapper,
                          ProductImageMapper productImageMapper,
                          SkuMapper skuMapper,
                          InventoryMapper inventoryMapper,
                          StoreMapper storeMapper,
                          CollectionMapper collectionMapper,
                          CollectionProductMapper collectionProductMapper,
                          LookMapper lookMapper,
                          HomeThemeMapper homeThemeMapper,
                          HomeModuleMapper homeModuleMapper) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.skuMapper = skuMapper;
        this.inventoryMapper = inventoryMapper;
        this.storeMapper = storeMapper;
        this.collectionMapper = collectionMapper;
        this.collectionProductMapper = collectionProductMapper;
        this.lookMapper = lookMapper;
        this.homeThemeMapper = homeThemeMapper;
        this.homeModuleMapper = homeModuleMapper;
    }

    public List<StoreView> stores() {
        return onlineStores().stream().map(CatalogService::toStoreView).toList();
    }

    public StoreView store(Long id) {
        Store store = storeMapper.selectById(id);
        if (store == null || !ON.equals(store.getStatus())) {
            return null;
        }
        return toStoreView(store);
    }

    public List<ProductCard> products(ProductFilter filter) {
        Long storeId = filter.storeId() != null ? filter.storeId() : defaultStoreId();

        LambdaQueryWrapper<Product> query = Wrappers.<Product>lambdaQuery().eq(Product::getStatus, ON);
        if (hasText(filter.category())) {
            query.eq(Product::getCategory, filter.category());
        }
        if (hasText(filter.material())) {
            query.eq(Product::getMaterial, filter.material());
        }
        if (hasText(filter.keyword())) {
            query.and(w -> w.like(Product::getTitle, filter.keyword())
                    .or().like(Product::getSubtitle, filter.keyword()));
        }
        if (hasText(filter.color()) || hasText(filter.size())) {
            LambdaQueryWrapper<Sku> skuQuery = Wrappers.<Sku>lambdaQuery().eq(Sku::getStatus, ON);
            if (hasText(filter.color())) {
                skuQuery.eq(Sku::getColor, filter.color());
            }
            if (hasText(filter.size())) {
                skuQuery.eq(Sku::getSize, filter.size());
            }
            Set<Long> productIds = skuMapper.selectList(skuQuery).stream()
                    .map(Sku::getProductId)
                    .collect(Collectors.toSet());
            if (productIds.isEmpty()) {
                return List.of();
            }
            query.in(Product::getId, productIds);
        }

        List<Product> products = productMapper.selectList(query);
        String sort = filter.sort() == null ? "recommend" : filter.sort();
        if ("recommend".equals(sort)) {
            products.sort(Comparator.comparing(Product::getSortWeight,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("new".equals(sort)) {
            products.sort(Comparator.comparing(Product::getOnlineAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        List<ProductCard> cards = buildCards(products, storeId);
        List<ProductCard> result = filter.onlyStock()
                ? cards.stream().filter(c -> !StockStatus.OUT.name().equals(c.stockStatus())).toList()
                : cards;
        if ("priceAsc".equals(sort)) {
            return result.stream().sorted(Comparator.comparingInt(ProductCard::priceCent)).toList();
        }
        if ("priceDesc".equals(sort)) {
            return result.stream().sorted(Comparator.comparingInt(ProductCard::priceCent).reversed()).toList();
        }
        return result;
    }

    public ProductDetailView product(Long id, Long storeId) {
        Product product = productMapper.selectById(id);
        if (product == null || !ON.equals(product.getStatus())) {
            return null;
        }
        Long currentStoreId = storeId != null ? storeId : defaultStoreId();

        List<ProductImage> images = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id)
                .orderByAsc(ProductImage::getSortNo));

        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, id)
                .eq(Sku::getStatus, ON)
                .orderByAsc(Sku::getSortNo));

        List<Long> skuIds = skus.stream().map(Sku::getId).toList();
        List<Inventory> inventories = skuIds.isEmpty()
                ? List.of()
                : inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                .in(Inventory::getSkuId, skuIds)
                .eq(Inventory::getOnSale, true));

        Map<Long, Integer> quantityBySku = inventories.stream()
                .filter(i -> Objects.equals(i.getStoreId(), currentStoreId))
                .collect(Collectors.toMap(Inventory::getSkuId, i -> nz(i.getQuantity()), (a, b) -> a));

        Map<Long, List<Inventory>> inventoriesByStore = inventories.stream()
                .collect(Collectors.groupingBy(Inventory::getStoreId));
        Map<Long, Map<Long, Integer>> quantityByStoreSku = inventories.stream()
                .collect(Collectors.groupingBy(Inventory::getStoreId,
                        Collectors.toMap(Inventory::getSkuId, i -> nz(i.getQuantity()), (a, b) -> a)));

        List<Store> stores = onlineStores();
        List<ProductDetailView.StoreStockView> storeStocks = stores.stream()
                .map(store -> {
                    List<Inventory> rows = inventoriesByStore.getOrDefault(store.getId(), List.of());
                    int quantity = rows.stream().mapToInt(i -> nz(i.getQuantity())).sum();
                    LocalDateTime updatedAt = rows.stream()
                            .map(Inventory::getUpdatedAt)
                            .filter(Objects::nonNull)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    return new ProductDetailView.StoreStockView(store.getId(), store.getName(),
                            store.getAddress(), quantity, updatedAt);
                })
                .toList();

        int minPrice = skus.stream().mapToInt(s -> nz(s.getPriceCent())).min().orElse(0);
        int minOrigin = skus.stream().mapToInt(s -> nz(s.getOriginPriceCent()))
                .filter(v -> v > 0).min().orElse(0);

        List<ProductDetailView.ImageView> imageViews = images.stream()
                .map(i -> new ProductDetailView.ImageView(i.getUrl(), nz(i.getWidth()), nz(i.getHeight())))
                .toList();
        List<ProductDetailView.SkuView> skuViews = skus.stream()
                .map(s -> {
                    int quantity = quantityBySku.getOrDefault(s.getId(), 0);
                    List<ProductDetailView.SkuStoreStockView> perStore = stores.stream()
                            .map(store -> new ProductDetailView.SkuStoreStockView(store.getId(),
                                    quantityByStoreSku.getOrDefault(store.getId(), Map.of())
                                            .getOrDefault(s.getId(), 0)))
                            .toList();
                    return new ProductDetailView.SkuView(s.getId(), s.getColor(), s.getSize(),
                            nz(s.getPriceCent()), nz(s.getOriginPriceCent()),
                            quantity, StockStatus.of(quantity).name(), perStore);
                })
                .toList();

        return new ProductDetailView(product.getId(), product.getTitle(), product.getSubtitle(),
                product.getCategory(), product.getSeason(), product.getMaterial(), product.getDescription(),
                product.getCoverUrl(), nz(product.getCoverWidth()), nz(product.getCoverHeight()),
                minPrice, minOrigin, nz(product.getLikeCount()), nz(product.getFavCount()),
                imageViews, skuViews, storeStocks, false, false, false);
    }

    public HomeView home(Long storeId) {
        LocalDateTime now = LocalDateTime.now();
        HomeTheme theme = homeThemeMapper.selectOne(Wrappers.<HomeTheme>lambdaQuery()
                .eq(HomeTheme::getStatus, ON)
                .and(w -> w.isNull(HomeTheme::getStartAt).or().le(HomeTheme::getStartAt, now))
                .and(w -> w.isNull(HomeTheme::getEndAt).or().ge(HomeTheme::getEndAt, now))
                .orderByDesc(HomeTheme::getIsCurrent)
                .orderByDesc(HomeTheme::getStartAt)
                .orderByDesc(HomeTheme::getId)
                .last("LIMIT 1"));
        if (theme == null) {
            return new HomeView(null, List.of());
        }
        Long currentStoreId = storeId != null ? storeId : defaultStoreId();
        List<HomeModule> modules = homeModuleMapper.selectList(Wrappers.<HomeModule>lambdaQuery()
                .eq(HomeModule::getThemeId, theme.getId())
                .eq(HomeModule::getEnabled, true)
                .orderByAsc(HomeModule::getSortNo));

        List<HomeView.Module> views = new ArrayList<>();
        for (HomeModule module : modules) {
            switch (module.getType()) {
                case "BANNER" -> views.add(new HomeView.Module("BANNER", module.getImageUrl(),
                        theme.getName(), theme.getCopyText(), List.of(), List.of(), null));
                case "RECOMMEND" -> views.add(new HomeView.Module("RECOMMEND", module.getImageUrl(),
                        "店长推荐", "这一季最常被拿起来试的几件",
                        buildCards(topProducts(4), currentStoreId), List.of(), null));
                case "COLLECTION" -> views.add(collectionModule(module, currentStoreId));
                case "LOOK" -> views.add(new HomeView.Module("LOOK", module.getImageUrl(),
                        "造型灵感", "一套穿好再进店", List.of(), lookCards(10), null));
                case "STORE" -> {
                    Store store = module.getRefId() == null ? firstOnlineStore() : storeMapper.selectById(module.getRefId());
                    if (store != null && ON.equals(store.getStatus())) {
                        views.add(new HomeView.Module("STORE", module.getImageUrl(), null, null,
                                List.of(), List.of(), toStoreView(store)));
                    }
                }
                default -> {
                }
            }
        }
        HomeView.Theme themeView = new HomeView.Theme(theme.getId(), theme.getName(), theme.getCopyText(),
                theme.getCoverUrl());
        return new HomeView(themeView, views);
    }

    private HomeView.Module collectionModule(HomeModule module, Long storeId) {
        Collection collection = module.getRefId() == null ? null : collectionMapper.selectById(module.getRefId());
        if (collection == null) {
            return new HomeView.Module("COLLECTION", module.getImageUrl(), null, null,
                    List.of(), List.of(), null);
        }
        List<Long> productIds = collectionProductMapper.selectList(Wrappers.<CollectionProduct>lambdaQuery()
                        .eq(CollectionProduct::getCollectionId, collection.getId())
                        .orderByAsc(CollectionProduct::getSortNo))
                .stream().map(CollectionProduct::getProductId).toList();
        List<Product> products = productIds.isEmpty() ? List.of() : productMapper.selectList(
                Wrappers.<Product>lambdaQuery().in(Product::getId, productIds).eq(Product::getStatus, ON));
        Map<Long, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> ordered = productIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new HomeView.Module("COLLECTION", module.getImageUrl(), collection.getName(),
                collection.getStory(), buildCards(ordered, storeId), List.of(), null);
    }

    private List<Product> topProducts(int limit) {
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, ON)
                .orderByDesc(Product::getSortWeight)
                .last("LIMIT " + limit));
    }

    private List<LookCard> lookCards(int limit) {
        List<Look> looks = lookMapper.selectList(Wrappers.<Look>lambdaQuery()
                .eq(Look::getStatus, ON)
                .orderByAsc(Look::getSortNo)
                .last("LIMIT " + limit));
        return looks.stream()
                .map(l -> new LookCard(l.getId(), l.getTitle(), l.getDescription(), l.getCoverUrl(),
                        nz(l.getCoverWidth()), nz(l.getCoverHeight()), nz(l.getLikeCount())))
                .toList();
    }

    private List<ProductCard> buildCards(List<Product> products, Long storeId) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .in(Sku::getProductId, productIds)
                .eq(Sku::getStatus, ON));
        Map<Long, List<Sku>> skusByProduct = skus.stream().collect(Collectors.groupingBy(Sku::getProductId));

        Map<Long, Integer> quantityBySku = new HashMap<>();
        if (storeId != null && !skus.isEmpty()) {
            inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery()
                            .eq(Inventory::getStoreId, storeId)
                            .in(Inventory::getSkuId, skus.stream().map(Sku::getId).toList())
                            .eq(Inventory::getOnSale, true))
                    .forEach(i -> quantityBySku.merge(i.getSkuId(), nz(i.getQuantity()), Integer::sum));
        }

        return products.stream().map(product -> {
            List<Sku> productSkus = skusByProduct.getOrDefault(product.getId(), List.of());
            int minPrice = productSkus.stream().mapToInt(s -> nz(s.getPriceCent())).min().orElse(0);
            int minOrigin = productSkus.stream().mapToInt(s -> nz(s.getOriginPriceCent()))
                    .filter(v -> v > 0).min().orElse(0);
            int totalQuantity = productSkus.stream()
                    .mapToInt(s -> quantityBySku.getOrDefault(s.getId(), 0)).sum();
            return new ProductCard(product.getId(), product.getTitle(), product.getCoverUrl(),
                    nz(product.getCoverWidth()), nz(product.getCoverHeight()),
                    minPrice, minOrigin, nz(product.getLikeCount()), nz(product.getFavCount()),
                    StockStatus.of(totalQuantity).name());
        }).toList();
    }

    private List<Store> onlineStores() {
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                .eq(Store::getStatus, ON)
                .orderByAsc(Store::getSortNo));
    }

    private Store firstOnlineStore() {
        return storeMapper.selectOne(Wrappers.<Store>lambdaQuery()
                .eq(Store::getStatus, ON)
                .orderByAsc(Store::getSortNo)
                .last("LIMIT 1"));
    }

    public Long defaultStoreId() {
        Store store = firstOnlineStore();
        return store == null ? null : store.getId();
    }

    private static StoreView toStoreView(Store store) {
        return new StoreView(store.getId(), store.getName(), store.getCode(), store.getAddress(),
                store.getPhone(), store.getOpenTime(), store.getCloseTime(), store.getLat(), store.getLng(),
                store.getCoverUrl());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
