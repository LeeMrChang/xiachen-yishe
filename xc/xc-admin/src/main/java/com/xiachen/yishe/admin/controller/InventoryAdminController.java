package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.InventoryMatrix;
import com.xiachen.yishe.admin.dto.InventoryUpsertRequest;
import com.xiachen.yishe.admin.service.InventoryAdminService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {

    private final InventoryAdminService inventoryAdminService;
    private final AuditRecorder audit;

    public InventoryAdminController(InventoryAdminService inventoryAdminService, AuditRecorder audit) {
        this.inventoryAdminService = inventoryAdminService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<InventoryMatrix> matrix(@RequestParam Long productId) {
        return ApiResponse.ok(inventoryAdminService.matrix(productId));
    }

    @PutMapping
    public ApiResponse<Integer> upsert(@Valid @RequestBody InventoryUpsertRequest request,
                                       HttpServletRequest http) {
        int count = inventoryAdminService.upsert(request);
        audit.record(http, "INVENTORY_UPDATE", "INVENTORY", null, Map.of("count", count));
        return ApiResponse.ok(count);
    }
}
