package com.xiachen.yishe.api.controller;

import com.xiachen.yishe.api.dto.TryonAddRequest;
import com.xiachen.yishe.api.dto.TryonGroup;
import com.xiachen.yishe.api.dto.TryonMoveRequest;
import com.xiachen.yishe.api.security.UserContext;
import com.xiachen.yishe.api.service.TryonService;
import com.xiachen.yishe.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tryon-items")
public class TryonController {

    private final TryonService tryonService;

    public TryonController(TryonService tryonService) {
        this.tryonService = tryonService;
    }

    @GetMapping
    public ApiResponse<List<TryonGroup>> list() {
        return ApiResponse.ok(tryonService.list(UserContext.requireUserId()));
    }

    @PostMapping
    public ApiResponse<Void> add(@Valid @RequestBody TryonAddRequest request) {
        tryonService.add(UserContext.requireUserId(), request.skuId(), request.storeId());
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> move(@PathVariable Long id, @Valid @RequestBody TryonMoveRequest request) {
        tryonService.move(UserContext.requireUserId(), id, request.storeId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        tryonService.remove(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}
