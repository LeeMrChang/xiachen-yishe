package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.CollectionDetail;
import com.xiachen.yishe.admin.dto.CollectionItemsRequest;
import com.xiachen.yishe.admin.dto.CollectionRow;
import com.xiachen.yishe.admin.dto.CollectionSaveRequest;
import com.xiachen.yishe.admin.service.CollectionAdminService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/collections")
public class CollectionAdminController {

    private final CollectionAdminService collectionAdminService;
    private final AuditRecorder audit;

    public CollectionAdminController(CollectionAdminService collectionAdminService, AuditRecorder audit) {
        this.collectionAdminService = collectionAdminService;
        this.audit = audit;
    }

    /** 列表同时供「首页主题」的系列选择器使用，保留 id / name / status 字段。 */
    @GetMapping
    public ApiResponse<List<CollectionRow>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(collectionAdminService.list(keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<CollectionDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(collectionAdminService.detail(id));
    }

    @PostMapping
    public ApiResponse<CollectionDetail> create(@Valid @RequestBody CollectionSaveRequest request,
                                                HttpServletRequest http) {
        CollectionDetail detail = collectionAdminService.create(request);
        audit.record(http, "COLLECTION_CREATE", "COLLECTION", detail.id(), Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}")
    public ApiResponse<CollectionDetail> update(@PathVariable Long id,
                                                @Valid @RequestBody CollectionSaveRequest request,
                                                HttpServletRequest http) {
        CollectionDetail detail = collectionAdminService.update(id, request);
        audit.record(http, "COLLECTION_UPDATE", "COLLECTION", id, Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}/items")
    public ApiResponse<CollectionDetail> saveItems(@PathVariable Long id,
                                                   @Valid @RequestBody CollectionItemsRequest request,
                                                   HttpServletRequest http) {
        CollectionDetail detail = collectionAdminService.saveItems(id, request);
        audit.record(http, "COLLECTION_ITEMS", "COLLECTION", id,
                Map.of("productCount", request.productIds().size()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<CollectionDetail> publish(@PathVariable Long id, HttpServletRequest http) {
        CollectionDetail detail = collectionAdminService.publish(id);
        audit.record(http, "COLLECTION_PUBLISH", "COLLECTION", id, Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<CollectionDetail> offline(@PathVariable Long id, HttpServletRequest http) {
        CollectionDetail detail = collectionAdminService.offline(id);
        audit.record(http, "COLLECTION_OFFLINE", "COLLECTION", id, Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        collectionAdminService.delete(id);
        audit.record(http, "COLLECTION_DELETE", "COLLECTION", id, Map.of());
        return ApiResponse.ok();
    }
}
