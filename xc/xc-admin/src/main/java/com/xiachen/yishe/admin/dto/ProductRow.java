package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;

public record ProductRow(Long id, String title, String coverUrl, String category, String status,
                         int priceCent, int totalQuantity, int skuCount,
                         LocalDateTime onlineAt, LocalDateTime updatedAt) {
}
