package com.xiachen.yishe.domain.view;

import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailView(
        Long id,
        String title,
        String subtitle,
        String category,
        String season,
        String material,
        String description,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        int priceCent,
        int originPriceCent,
        int likeCount,
        int favCount,
        List<ImageView> images,
        List<SkuView> skus,
        List<StoreStockView> stores,
        boolean favorited,
        boolean liked,
        boolean inCart
) {
    public ProductDetailView withUserState(boolean favorited, boolean liked, boolean inCart) {
        return new ProductDetailView(id, title, subtitle, category, season, material, description,
                coverUrl, coverWidth, coverHeight, priceCent, originPriceCent, likeCount, favCount,
                images, skus, stores, favorited, liked, inCart);
    }

    public record ImageView(String url, int width, int height) {
    }

    public record SkuView(Long id, String color, String size, int priceCent, int originPriceCent,
                          int stockQuantity, String stockStatus, List<SkuStoreStockView> storeStocks) {
    }

    public record SkuStoreStockView(Long storeId, int quantity) {
    }

    public record StoreStockView(Long storeId, String storeName, String address, int quantity,
                                 LocalDateTime updatedAt) {
    }
}
