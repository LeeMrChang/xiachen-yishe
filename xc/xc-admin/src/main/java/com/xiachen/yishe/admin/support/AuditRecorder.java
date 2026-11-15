package com.xiachen.yishe.admin.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiachen.yishe.admin.security.AdminContext;
import com.xiachen.yishe.domain.entity.AuditLog;
import com.xiachen.yishe.domain.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditRecorder(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void record(HttpServletRequest request, String action, String targetType, Long targetId, Object detail) {
        Long staffId = AdminContext.staffId();
        if (staffId == null) {
            log.warn("跳过审计：缺少操作人上下文 action={}", action);
            return;
        }
        try {
            AuditLog entry = new AuditLog();
            entry.setStaffId(staffId);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail == null ? null : objectMapper.writeValueAsString(detail));
            entry.setIp(clientIp(request));
            auditLogMapper.insert(entry);
        } catch (JsonProcessingException e) {
            log.warn("审计日志序列化失败 action={} target={}#{}", action, targetType, targetId, e);
        } catch (Exception e) {
            log.warn("审计日志写入失败 action={} target={}#{}", action, targetType, targetId, e);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
