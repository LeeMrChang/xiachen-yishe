package com.xiachen.yishe.admin.security;

public record StaffPrincipal(Long id, String username, String displayName, String role, Long storeId) {
}
