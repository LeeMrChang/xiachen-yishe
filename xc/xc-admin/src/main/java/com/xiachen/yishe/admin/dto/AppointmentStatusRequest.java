package com.xiachen.yishe.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AppointmentStatusRequest(@NotBlank String status) {
}
