package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSaveRequest(
        @NotBlank(message = "请填写商品标题") @Size(max = 128, message = "标题不能超过 128 字") String title,
        @Size(max = 255, message = "副标题不能超过 255 字") String subtitle,
        @Size(max = 32) String category,
        @Size(max = 16) String season,
        @Size(max = 64) String material,
        String description,
        @Min(value = 0, message = "排序权重不能为负") Integer sortWeight) {
}
