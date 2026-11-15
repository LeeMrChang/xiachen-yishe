package com.xiachen.yishe.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WxLoginRequest(
        @NotBlank @Size(max = 128) String code,
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatarUrl
) {
}
