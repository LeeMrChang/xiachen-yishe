package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CollectionItemsRequest(
        @NotNull(message = "缺少商品列表") @Size(max = 200, message = "单个系列最多 200 件商品")
        List<@NotNull @Positive Long> productIds
) {
}
