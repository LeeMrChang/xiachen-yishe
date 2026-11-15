package com.xiachen.yishe.api.dto;

public record UserTargetItem(
        int targetType,
        Long targetId,
        String title,
        String subtitle,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        Integer priceCent,
        int likeCount
) {
}
