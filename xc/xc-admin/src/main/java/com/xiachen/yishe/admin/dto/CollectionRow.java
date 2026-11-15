package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;

public record CollectionRow(
        Long id,
        String name,
        String coverUrl,
        String story,
        LocalDateTime publishAt,
        String status,
        int sortNo,
        int productCount,
        LocalDateTime updatedAt
) {
}
