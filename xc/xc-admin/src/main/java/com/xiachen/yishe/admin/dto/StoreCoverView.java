package com.xiachen.yishe.admin.dto;

import java.time.LocalTime;

public record StoreCoverView(Long id, String name, String code, String address, String phone,
                             LocalTime openTime, LocalTime closeTime, String coverUrl, String status) {
}
