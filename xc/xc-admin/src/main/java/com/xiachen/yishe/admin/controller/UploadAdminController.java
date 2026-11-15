package com.xiachen.yishe.admin.controller;

import com.xiachen.yishe.admin.dto.UploadResult;
import com.xiachen.yishe.admin.service.UploadService;
import com.xiachen.yishe.admin.support.AuditRecorder;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/uploads")
public class UploadAdminController {

    private final UploadService uploadService;
    private final AuditRecorder audit;

    public UploadAdminController(UploadService uploadService, AuditRecorder audit) {
        this.uploadService = uploadService;
        this.audit = audit;
    }

    @PostMapping
    public ApiResponse<UploadResult> upload(@RequestParam("file") MultipartFile file,
                                            HttpServletRequest http) {
        if (file == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的图片");
        }
        UploadResult result = uploadService.store(file);
        audit.record(http, "UPLOAD", "FILE", null, Map.of("url", result.url()));
        return ApiResponse.ok(result);
    }
}
