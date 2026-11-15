package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LookAdminDetail(
        Long id,
        String title,
        String description,
        String coverUrl,
        int coverWidth,
        int coverHeight,
        String status,
        int sortNo,
        LocalDateTime updatedAt,
        List<Item> items
) {

    public record Item(
            Long productId,
            String title,
            String coverUrl,
            int priceCent,
            String status,
            int sortNo
    ) {
    }
}
