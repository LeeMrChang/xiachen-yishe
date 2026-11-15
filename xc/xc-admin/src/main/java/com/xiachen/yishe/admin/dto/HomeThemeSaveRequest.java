package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record HomeThemeSaveRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String copyText,
        @Size(max = 512) String coverUrl,
        LocalDateTime startAt,
        LocalDateTime endAt,
        @Pattern(regexp = "DRAFT|ON|OFF", message = "状态只能是 DRAFT / ON / OFF") String status
) {
}
