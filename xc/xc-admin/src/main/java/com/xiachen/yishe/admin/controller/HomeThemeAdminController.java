package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.HomeModulesRequest;
import com.xiachen.yishe.admin.dto.HomeThemeDetail;
import com.xiachen.yishe.admin.dto.HomeThemeRow;
import com.xiachen.yishe.admin.dto.HomeThemeSaveRequest;
import com.xiachen.yishe.admin.service.HomeThemeAdminService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/home-themes")
public class HomeThemeAdminController {

    private final HomeThemeAdminService homeThemeAdminService;
    private final AuditRecorder audit;

    public HomeThemeAdminController(HomeThemeAdminService homeThemeAdminService, AuditRecorder audit) {
        this.homeThemeAdminService = homeThemeAdminService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<HomeThemeRow>> list() {
        return ApiResponse.ok(homeThemeAdminService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<HomeThemeDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(homeThemeAdminService.detail(id));
    }

    @PostMapping
    public ApiResponse<HomeThemeDetail> create(@Valid @RequestBody HomeThemeSaveRequest request,
                                               HttpServletRequest http) {
        HomeThemeDetail detail = homeThemeAdminService.create(request);
        audit.record(http, "HOME_THEME_CREATE", "HOME_THEME", detail.id(), Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}")
    public ApiResponse<HomeThemeDetail> update(@PathVariable Long id,
                                               @Valid @RequestBody HomeThemeSaveRequest request,
                                               HttpServletRequest http) {
        HomeThemeDetail detail = homeThemeAdminService.update(id, request);
        audit.record(http, "HOME_THEME_UPDATE", "HOME_THEME", id, Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}/modules")
    public ApiResponse<HomeThemeDetail> saveModules(@PathVariable Long id,
                                                    @Valid @RequestBody HomeModulesRequest request,
                                                    HttpServletRequest http) {
        HomeThemeDetail detail = homeThemeAdminService.saveModules(id, request);
        audit.record(http, "HOME_THEME_MODULES", "HOME_THEME", id,
                Map.of("count", request.modules().size()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<HomeThemeDetail> activate(@PathVariable Long id, HttpServletRequest http) {
        HomeThemeDetail detail = homeThemeAdminService.activate(id);
        audit.record(http, "HOME_THEME_ACTIVATE", "HOME_THEME", id, Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<HomeThemeDetail> offline(@PathVariable Long id, HttpServletRequest http) {
        HomeThemeDetail detail = homeThemeAdminService.offline(id);
        audit.record(http, "HOME_THEME_OFFLINE", "HOME_THEME", id, Map.of("name", detail.name()));
        return ApiResponse.ok(detail);
    }
}
