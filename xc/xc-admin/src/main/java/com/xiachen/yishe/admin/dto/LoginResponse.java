package com.xiachen.yishe.admin.dto;

public record LoginResponse(String token, long expiresIn, Profile staff) {

    public record Profile(Long id, String username, String displayName, String role, Long storeId) {
    }
}
