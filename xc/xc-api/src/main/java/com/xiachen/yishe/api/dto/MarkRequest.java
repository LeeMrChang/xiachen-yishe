package com.xiachen.yishe.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkRequest(
        @NotNull @Min(1) @Max(2) Integer targetType,
        @NotNull @Positive Long targetId
) {
}
