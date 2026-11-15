package com.xiachen.yishe.api.controller;

import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.domain.service.CatalogService;
import com.xiachen.yishe.domain.view.StoreView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final CatalogService catalogService;

    public StoreController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<List<StoreView>> list() {
        return ApiResponse.ok(catalogService.stores());
    }

    @GetMapping("/{id}")
    public ApiResponse<StoreView> detail(@PathVariable Long id) {
        StoreView store = catalogService.store(id);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return ApiResponse.ok(store);
    }
}
