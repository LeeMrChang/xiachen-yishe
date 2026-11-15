package com.xiachen.yishe.api.controller;

import com.xiachen.yishe.api.dto.AppointmentCreateRequest;
import com.xiachen.yishe.api.dto.AppointmentView;
import com.xiachen.yishe.api.dto.CreatedResult;
import com.xiachen.yishe.api.security.UserContext;
import com.xiachen.yishe.api.service.AppointmentService;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.common.web.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ApiResponse<CreatedResult> create(@Valid @RequestBody AppointmentCreateRequest request) {
        return ApiResponse.ok(appointmentService.create(UserContext.requireUserId(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<AppointmentView>> list(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return ApiResponse.ok(appointmentService.list(UserContext.requireUserId(), page, safeSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppointmentView> detail(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.detail(UserContext.requireUserId(), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        appointmentService.cancel(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}
