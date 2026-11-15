package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;

public record HomeThemeRow(Long id, String name, String coverUrl, String copyText,
                           LocalDateTime startAt, LocalDateTime endAt, String status,
                           boolean current, int moduleCount, LocalDateTime updatedAt) {
}
