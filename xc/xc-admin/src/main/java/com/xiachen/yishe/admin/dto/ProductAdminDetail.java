package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductAdminDetail(Long id, String title, String subtitle, String category, String season,
                                 String material, String description, String coverUrl,
                                 int coverWidth, int coverHeight, String status, int sortWeight,
                                 int likeCount, int favCount, LocalDateTime onlineAt,
                                 LocalDateTime updatedAt, List<ImageItem> images, List<SkuItem> skus) {

    public record ImageItem(Long id, String url, int width, int height, int sortNo) {
    }

    public record SkuItem(Long id, String color, String size, int priceCent, int originPriceCent,
                          int sortNo, String status, int totalQuantity) {
    }
}
