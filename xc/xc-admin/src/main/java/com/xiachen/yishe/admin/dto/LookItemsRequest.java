package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LookItemsRequest(
        @NotEmpty @Size(max = 12) List<@NotNull @Positive Long> productIds
) {
}
