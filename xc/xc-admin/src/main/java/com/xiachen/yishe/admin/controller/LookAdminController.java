package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.LookAdminDetail;
import com.xiachen.yishe.admin.dto.LookItemsRequest;
import com.xiachen.yishe.admin.dto.LookRow;
import com.xiachen.yishe.admin.dto.LookSaveRequest;
import com.xiachen.yishe.admin.service.LookAdminService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.common.web.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/looks")
public class LookAdminController {

    private final LookAdminService lookAdminService;
    private final AuditRecorder audit;

    public LookAdminController(LookAdminService lookAdminService, AuditRecorder audit) {
        this.lookAdminService = lookAdminService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<PageResult<LookRow>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return ApiResponse.ok(lookAdminService.list(keyword, status, page,
                Math.min(Math.max(pageSize, 1), 100)));
    }

    @GetMapping("/{id}")
    public ApiResponse<LookAdminDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(lookAdminService.detail(id));
    }

    @PostMapping
    public ApiResponse<LookAdminDetail> create(@Valid @RequestBody LookSaveRequest request,
                                               HttpServletRequest http) {
        LookAdminDetail detail = lookAdminService.create(request);
        audit.record(http, "LOOK_CREATE", "LOOK", detail.id(), Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}")
    public ApiResponse<LookAdminDetail> update(@PathVariable Long id,
                                               @Valid @RequestBody LookSaveRequest request,
                                               HttpServletRequest http) {
        LookAdminDetail detail = lookAdminService.update(id, request);
        audit.record(http, "LOOK_UPDATE", "LOOK", id, Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}/items")
    public ApiResponse<LookAdminDetail> saveItems(@PathVariable Long id,
                                                  @Valid @RequestBody LookItemsRequest request,
                                                  HttpServletRequest http) {
        LookAdminDetail detail = lookAdminService.saveItems(id, request);
        audit.record(http, "LOOK_ITEMS", "LOOK", id, Map.of("count", request.productIds().size()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<LookAdminDetail> publish(@PathVariable Long id, HttpServletRequest http) {
        LookAdminDetail detail = lookAdminService.publish(id);
        audit.record(http, "LOOK_PUBLISH", "LOOK", id, Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<LookAdminDetail> offline(@PathVariable Long id, HttpServletRequest http) {
        LookAdminDetail detail = lookAdminService.offline(id);
        audit.record(http, "LOOK_OFFLINE", "LOOK", id, Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }
}
