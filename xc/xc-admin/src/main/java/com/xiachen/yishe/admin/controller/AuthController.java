package com.xiachen.yishe.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.admin.dto.LoginRequest;
import com.xiachen.yishe.admin.dto.LoginResponse;
import com.xiachen.yishe.admin.security.AdminContext;
import com.xiachen.yishe.admin.security.JwtService;
import com.xiachen.yishe.admin.security.StaffPrincipal;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.domain.entity.Staff;
import com.xiachen.yishe.domain.mapper.StaffMapper;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(StaffMapper staffMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.staffMapper = staffMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Staff staff = staffMapper.selectOne(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getUsername, request.username()));
        if (staff == null || !passwordEncoder.matches(request.password(), staff.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (!"ON".equals(staff.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        staff.setLastLoginAt(LocalDateTime.now());
        staffMapper.updateById(staff);
        return ApiResponse.ok(new LoginResponse(jwtService.issue(staff), jwtService.ttlSeconds(),
                new LoginResponse.Profile(staff.getId(), staff.getUsername(), staff.getDisplayName(),
                        staff.getRole(), staff.getStoreId())));
    }

    @GetMapping("/me")
    public ApiResponse<StaffPrincipal> me() {
        return ApiResponse.ok(AdminContext.get());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 无状态 JWT，前端丢弃 token 即可
        return ApiResponse.ok();
    }
}
