package com.xiachen.yishe.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SkuBatchRequest(@NotEmpty(message = "至少保留一个 SKU") List<@Valid Item> items) {

    public record Item(
            @NotBlank(message = "请填写颜色") @Size(max = 32) String color,
            @NotBlank(message = "请填写尺码") @Size(max = 16) String size,
            @NotNull(message = "请填写售价") @Min(value = 1, message = "售价必须大于 0") Integer priceCent,
            @Min(value = 0, message = "划线价不能为负") Integer originPriceCent) {
    }
}
