package com.xiachen.yishe.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InventoryUpsertRequest(@NotEmpty(message = "没有需要保存的库存") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "缺少 SKU") Long skuId,
            @NotNull(message = "缺少门店") Long storeId,
            @NotNull(message = "请填写库存数量") @Min(value = 0, message = "库存不能为负") Integer quantity,
            Boolean onSale) {
    }
}
