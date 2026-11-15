package com.xiachen.yishe.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AppointmentRow(
        Long id,
        LocalDate visitDate,
        LocalTime visitTime,
        String status,
        String statusText,
        Long storeId,
        String storeName,
        int itemCount,
        String phone,
        String contactName,
        String remark,
        LocalDateTime createdAt
) {
}
