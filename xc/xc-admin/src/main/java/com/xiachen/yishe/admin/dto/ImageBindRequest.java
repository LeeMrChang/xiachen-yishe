package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageBindRequest(
        @NotBlank(message = "请先上传图片") @Size(max = 512) String url,
        @Min(value = 0, message = "宽度不能为负") Integer width,
        @Min(value = 0, message = "高度不能为负") Integer height) {
}
