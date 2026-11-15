package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.AppointmentBoardDetail;
import com.xiachen.yishe.admin.dto.AppointmentRow;
import com.xiachen.yishe.admin.dto.AppointmentStatusRequest;
import com.xiachen.yishe.admin.service.AppointmentAdminService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.common.web.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/appointments")
public class AppointmentAdminController {

    private final AppointmentAdminService appointmentAdminService;
    private final AuditRecorder audit;

    public AppointmentAdminController(AppointmentAdminService appointmentAdminService, AuditRecorder audit) {
        this.appointmentAdminService = appointmentAdminService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<PageResult<AppointmentRow>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return ApiResponse.ok(appointmentAdminService.list(date, status, storeId, page,
                Math.min(Math.max(pageSize, 1), 100)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppointmentBoardDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(appointmentAdminService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AppointmentBoardDetail> updateStatus(@PathVariable Long id,
                                                            @Valid @RequestBody AppointmentStatusRequest request,
                                                            HttpServletRequest http) {
        AppointmentAdminService.StatusChange change =
                appointmentAdminService.updateStatus(id, request.status());
        audit.record(http, "APPOINTMENT_STATUS", "APPOINTMENT", id,
                Map.of("from", change.fromStatus(), "to", change.appointment().status()));
        return ApiResponse.ok(change.appointment());
    }
}
