package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CollectionSaveRequest(
        @NotBlank(message = "请填写系列名称") @Size(max = 64, message = "系列名称不能超过 64 字") String name,
        @Size(max = 512) String coverUrl,
        @Size(max = 5000, message = "系列故事不能超过 5000 字") String story,
        LocalDateTime publishAt,
        @Pattern(regexp = "DRAFT|ON|OFF", message = "状态只能是 DRAFT / ON / OFF") String status,
        @PositiveOrZero(message = "排序不能为负") Integer sortNo) {
}
