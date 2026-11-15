package com.xiachen.yishe.api.controller;

import com.xiachen.yishe.api.dto.LoginResponse;
import com.xiachen.yishe.api.dto.ProfileUpdateRequest;
import com.xiachen.yishe.api.dto.UserProfile;
import com.xiachen.yishe.api.dto.WxLoginRequest;
import com.xiachen.yishe.api.security.UserContext;
import com.xiachen.yishe.api.security.UserJwtService;
import com.xiachen.yishe.api.service.AppUserService;
import com.xiachen.yishe.api.service.WechatClient;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.domain.entity.AppUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthController {

    private final WechatClient wechatClient;
    private final AppUserService appUserService;
    private final UserJwtService jwtService;

    public UserAuthController(WechatClient wechatClient, AppUserService appUserService,
                              UserJwtService jwtService) {
        this.wechatClient = wechatClient;
        this.appUserService = appUserService;
        this.jwtService = jwtService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<LoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request,
                                              @RequestHeader(value = "X-App-Id", required = false) String appId) {
        WechatClient.Session session = wechatClient.code2Session(appId, request.code());
        AppUser user = appUserService.login(appId, session.openid(), session.unionid());
        if (hasText(request.nickname()) || hasText(request.avatarUrl())) {
            user = appUserService.updateProfile(user.getId(), request.nickname(), request.avatarUrl());
        }
        return ApiResponse.ok(new LoginResponse(jwtService.issue(user), jwtService.ttlSeconds(),
                UserProfile.of(user)));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me() {
        return ApiResponse.ok(UserProfile.of(appUserService.get(UserContext.requireUserId())));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfile> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        AppUser user = appUserService.updateProfile(UserContext.requireUserId(),
                request.nickname(), request.avatarUrl());
        return ApiResponse.ok(UserProfile.of(user));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
