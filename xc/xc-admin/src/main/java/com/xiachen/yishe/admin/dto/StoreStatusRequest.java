package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StoreStatusRequest(
        @NotBlank(message = "请选择状态")
        @Pattern(regexp = "ON|OFF", message = "状态只能是 ON / OFF") String status) {
}
