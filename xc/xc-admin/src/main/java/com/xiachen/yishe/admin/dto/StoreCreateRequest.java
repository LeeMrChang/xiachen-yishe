package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record StoreCreateRequest(
        @NotBlank(message = "请填写门店编码")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "编码只能使用 2–32 位字母、数字、下划线或短横线") String code,
        @NotBlank(message = "请填写门店名") @Size(max = 64, message = "门店名不能超过 64 字") String name,
        @Size(max = 255, message = "地址不能超过 255 字") String address,
        @Size(max = 32, message = "电话不能超过 32 字") String phone,
        LocalTime openTime,
        LocalTime closeTime,
        @Size(max = 512, message = "图片地址过长") String coverUrl) {
}
