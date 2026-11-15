package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LookSaveRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 5000) String description,
        @Size(max = 512) String coverUrl,
        @PositiveOrZero Integer coverWidth,
        @PositiveOrZero Integer coverHeight,
        Integer sortNo
) {
}
