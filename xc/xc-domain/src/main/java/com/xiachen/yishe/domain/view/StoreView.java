package com.xiachen.yishe.domain.view;

import java.math.BigDecimal;
import java.time.LocalTime;

public record StoreView(
        Long id,
        String name,
        String code,
        String address,
        String phone,
        LocalTime openTime,
        LocalTime closeTime,
        BigDecimal lat,
        BigDecimal lng,
        String coverUrl
) {
}
