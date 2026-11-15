package com.xiachen.yishe.api.controller;

import com.xiachen.yishe.api.dto.FootprintItem;
import com.xiachen.yishe.api.security.UserContext;
import com.xiachen.yishe.api.service.FootprintService;
import com.xiachen.yishe.common.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/footprints")
public class FootprintController {

    private final FootprintService footprintService;

    public FootprintController(FootprintService footprintService) {
        this.footprintService = footprintService;
    }

    @GetMapping
    public ApiResponse<List<FootprintItem>> list(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(footprintService.list(UserContext.requireUserId(), limit));
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        footprintService.clear(UserContext.requireUserId());
        return ApiResponse.ok();
    }
}
