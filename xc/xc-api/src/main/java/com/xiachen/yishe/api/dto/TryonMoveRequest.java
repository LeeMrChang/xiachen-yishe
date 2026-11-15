package com.xiachen.yishe.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TryonMoveRequest(
        @NotNull @Positive Long storeId
) {
}
