package com.xiachen.yishe.domain.view;

public record ProductCard(
        Long id,
        String title,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        int priceCent,
        int originPriceCent,
        int likeCount,
        int favCount,
        String stockStatus
) {
}
