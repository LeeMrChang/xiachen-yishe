package com.xiachen.yishe.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AppointmentCreateRequest(
        @NotNull @Positive Long storeId,
        @NotNull LocalDate visitDate,
        LocalTime visitTime,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
        @Size(max = 32) String contactName,
        @Size(max = 255) String remark,
        @NotBlank @Size(max = 64) String clientRequestId,
        @NotEmpty @Size(max = 20) List<@NotNull @Positive Long> skuIds
) {
}
