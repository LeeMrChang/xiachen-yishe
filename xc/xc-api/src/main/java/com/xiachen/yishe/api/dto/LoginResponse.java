package com.xiachen.yishe.api.dto;

public record LoginResponse(String token, long expiresIn, UserProfile user) {
}
