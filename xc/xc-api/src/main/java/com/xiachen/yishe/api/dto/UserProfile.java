package com.xiachen.yishe.api.dto;

import com.xiachen.yishe.domain.entity.AppUser;

public record UserProfile(Long id, String nickname, String avatarUrl) {

    public static UserProfile of(AppUser user) {
        return new UserProfile(user.getId(), user.getNickname(), user.getAvatarUrl());
    }
}
