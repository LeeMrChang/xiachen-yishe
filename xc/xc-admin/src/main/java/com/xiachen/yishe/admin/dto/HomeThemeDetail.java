package com.xiachen.yishe.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HomeThemeDetail(Long id, String name, String coverUrl, String copyText,
                              LocalDateTime startAt, LocalDateTime endAt, String status,
                              boolean current, LocalDateTime updatedAt, List<Module> modules) {

    public record Module(Long id, String type, String typeLabel, Long refId, String refName,
                         String imageUrl, int sortNo, boolean enabled) {
    }
}
