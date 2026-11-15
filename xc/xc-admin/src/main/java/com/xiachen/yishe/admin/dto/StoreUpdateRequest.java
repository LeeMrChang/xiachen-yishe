package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record StoreUpdateRequest(
        @NotBlank(message = "请填写门店名") @Size(max = 64, message = "门店名不能超过 64 字") String name,
        @Size(max = 255, message = "地址不能超过 255 字") String address,
        @Size(max = 32, message = "电话不能超过 32 字") String phone,
        LocalTime openTime,
        LocalTime closeTime) {
}
