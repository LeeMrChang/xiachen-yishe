package com.xiachen.yishe.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record AppointmentBoardDetail(
        Long id,
        String status,
        String statusText,
        Long storeId,
        String storeName,
        String storeAddress,
        String storePhone,
        LocalDate visitDate,
        LocalTime visitTime,
        int itemCount,
        String phone,
        String contactName,
        String remark,
        LocalDateTime createdAt,
        List<Item> items
) {

    public record Item(
            Long skuId,
            Long productId,
            String title,
            String color,
            String size,
            String coverUrl,
            int priceCent
    ) {
    }
}
