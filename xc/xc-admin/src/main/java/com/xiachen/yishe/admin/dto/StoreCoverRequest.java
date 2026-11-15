package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreCoverRequest(
        @NotBlank(message = "请先上传门店封面") @Size(max = 512, message = "图片地址过长") String coverUrl) {
}
