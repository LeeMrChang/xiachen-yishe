package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.StoreCoverRequest;
import com.xiachen.yishe.admin.dto.StoreCoverView;
import com.xiachen.yishe.admin.dto.StoreCreateRequest;
import com.xiachen.yishe.admin.dto.StoreOption;
import com.xiachen.yishe.admin.dto.StoreStatusRequest;
import com.xiachen.yishe.admin.dto.StoreUpdateRequest;
import com.xiachen.yishe.admin.service.AppointmentAdminService;
import com.xiachen.yishe.admin.service.StoreAdminService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stores")
public class StoreAdminController {

    private final AppointmentAdminService appointmentAdminService;
    private final StoreAdminService storeAdminService;
    private final AuditRecorder audit;

    public StoreAdminController(AppointmentAdminService appointmentAdminService, StoreAdminService storeAdminService,
                                AuditRecorder audit) {
        this.appointmentAdminService = appointmentAdminService;
        this.storeAdminService = storeAdminService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<StoreOption>> list() {
        return ApiResponse.ok(appointmentAdminService.stores());
    }

    @GetMapping("/covers")
    public ApiResponse<List<StoreCoverView>> covers() {
        return ApiResponse.ok(storeAdminService.covers());
    }

    @PostMapping
    public ApiResponse<StoreCoverView> create(@Valid @RequestBody StoreCreateRequest request,
                                              HttpServletRequest http) {
        StoreCoverView view = storeAdminService.create(request);
        audit.record(http, "STORE_CREATE", "STORE", view.id(), Map.of("code", view.code(), "name", view.name()));
        return ApiResponse.ok(view);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<StoreCoverView> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody StoreStatusRequest request,
                                                    HttpServletRequest http) {
        StoreCoverView view = storeAdminService.updateStatus(id, request);
        audit.record(http, "STORE_STATUS", "STORE", id, Map.of("status", view.status()));
        return ApiResponse.ok(view);
    }

    @PutMapping("/{id}")
    public ApiResponse<StoreCoverView> update(@PathVariable Long id,
                                              @Valid @RequestBody StoreUpdateRequest request,
                                              HttpServletRequest http) {
        StoreCoverView view = storeAdminService.update(id, request);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", view.name());
        detail.put("address", view.address());
        detail.put("phone", view.phone());
        detail.put("openTime", view.openTime());
        detail.put("closeTime", view.closeTime());
        audit.record(http, "STORE_UPDATE", "STORE", id, detail);
        return ApiResponse.ok(view);
    }

    @PutMapping("/{id}/cover")
    public ApiResponse<StoreCoverView> updateCover(@PathVariable Long id,
                                                   @Valid @RequestBody StoreCoverRequest request,
                                                   HttpServletRequest http) {
        StoreCoverView view = storeAdminService.updateCover(id, request);
        audit.record(http, "STORE_COVER_UPDATE", "STORE", id, Map.of("coverUrl", view.coverUrl()));
        return ApiResponse.ok(view);
    }
}
