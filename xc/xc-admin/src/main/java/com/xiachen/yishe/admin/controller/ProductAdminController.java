package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.ImageBindRequest;
import com.xiachen.yishe.admin.dto.ProductAdminDetail;
import com.xiachen.yishe.admin.dto.ProductRow;
import com.xiachen.yishe.admin.dto.ProductSaveRequest;
import com.xiachen.yishe.admin.dto.SkuBatchRequest;
import com.xiachen.yishe.admin.service.ProductAdminService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.common.web.PageResult;
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

import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class ProductAdminController {

    private final ProductAdminService productAdminService;
    private final AuditRecorder audit;

    public ProductAdminController(ProductAdminService productAdminService, AuditRecorder audit) {
        this.productAdminService = productAdminService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<PageResult<ProductRow>> list(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return ApiResponse.ok(productAdminService.list(keyword, status, page,
                Math.min(Math.max(pageSize, 1), 100)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductAdminDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(productAdminService.detail(id));
    }

    @PostMapping
    public ApiResponse<ProductAdminDetail> create(@Valid @RequestBody ProductSaveRequest request,
                                                  HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.create(request);
        audit.record(http, "PRODUCT_CREATE", "PRODUCT", detail.id(), Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductAdminDetail> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProductSaveRequest request,
                                                  HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.update(id, request);
        audit.record(http, "PRODUCT_UPDATE", "PRODUCT", id, Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ProductAdminDetail> publish(@PathVariable Long id, HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.publish(id);
        audit.record(http, "PRODUCT_PUBLISH", "PRODUCT", id, Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<ProductAdminDetail> offline(@PathVariable Long id, HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.offline(id);
        audit.record(http, "PRODUCT_OFFLINE", "PRODUCT", id, Map.of("title", detail.title()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}/skus")
    public ApiResponse<ProductAdminDetail> saveSkus(@PathVariable Long id,
                                                    @Valid @RequestBody SkuBatchRequest request,
                                                    HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.saveSkus(id, request);
        audit.record(http, "SKU_SAVE", "PRODUCT", id, Map.of("skuCount", request.items().size()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/images")
    public ApiResponse<ProductAdminDetail> bindImage(@PathVariable Long id,
                                                     @Valid @RequestBody ImageBindRequest request,
                                                     HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.bindImage(id, request);
        audit.record(http, "IMAGE_BIND", "PRODUCT", id, Map.of("url", request.url()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/images/{imageId}/cover")
    public ApiResponse<ProductAdminDetail> setCover(@PathVariable Long id, @PathVariable Long imageId,
                                                    HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.setCover(id, imageId);
        audit.record(http, "IMAGE_COVER", "PRODUCT", id, Map.of("imageId", imageId));
        return ApiResponse.ok(detail);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<ProductAdminDetail> unbindImage(@PathVariable Long id, @PathVariable Long imageId,
                                                       HttpServletRequest http) {
        ProductAdminDetail detail = productAdminService.unbindImage(id, imageId);
        audit.record(http, "IMAGE_UNBIND", "PRODUCT", id, Map.of("imageId", imageId));
        return ApiResponse.ok(detail);
    }
}
