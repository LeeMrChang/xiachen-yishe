package com.xiachen.yishe.api.dto;

import java.time.LocalDateTime;

public record FootprintItem(
        Long productId,
        String title,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        Integer priceCent,
        Long storeId,
        String storeName,
        String stockStatus,
        int stockQuantity,
        LocalDateTime viewedAt
) {
}
