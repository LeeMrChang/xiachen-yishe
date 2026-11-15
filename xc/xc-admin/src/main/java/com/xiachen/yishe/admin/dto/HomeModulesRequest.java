package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HomeModulesRequest(
        @NotNull @Size(max = 8) List<@NotNull Item> modules
) {

    public record Item(@NotBlank String type, Long refId, @Size(max = 512) String imageUrl, Boolean enabled) {
    }
}
