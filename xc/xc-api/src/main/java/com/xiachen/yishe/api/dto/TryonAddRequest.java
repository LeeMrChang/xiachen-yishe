package com.xiachen.yishe.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TryonAddRequest(
        @NotNull @Positive Long skuId,
        @NotNull @Positive Long storeId
) {
}
