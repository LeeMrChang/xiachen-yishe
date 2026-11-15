package com.xiachen.yishe.api.dto;

public record TryonItemView(
        Long id,
        Long skuId,
        Long productId,
        String title,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        String color,
        String size,
        int priceCent,
        int stockQuantity,
        String stockStatus
) {
}
