package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CollectionDetail(
        Long id,
        String name,
        String coverUrl,
        String story,
        LocalDateTime publishAt,
        String status,
        int sortNo,
        LocalDateTime updatedAt,
        List<Item> products
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
