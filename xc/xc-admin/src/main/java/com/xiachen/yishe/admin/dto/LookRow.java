package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;

public record LookRow(
        Long id,
        String title,
        String description,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        String status,
        int sortNo,
        int itemCount,
        LocalDateTime updatedAt
) {
}
