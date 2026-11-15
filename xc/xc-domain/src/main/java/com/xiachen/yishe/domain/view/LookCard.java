package com.xiachen.yishe.domain.view;

public record LookCard(
        Long id,
        String title,
        String description,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        int likeCount
) {
}
