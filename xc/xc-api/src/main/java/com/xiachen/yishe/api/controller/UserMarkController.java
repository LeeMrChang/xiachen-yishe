package com.xiachen.yishe.api.controller;

import com.xiachen.yishe.api.dto.MarkRequest;
import com.xiachen.yishe.api.dto.UserTargetItem;
import com.xiachen.yishe.api.security.UserContext;
import com.xiachen.yishe.api.service.UserMarkService;
import com.xiachen.yishe.api.service.UserMarkService.Kind;
import com.xiachen.yishe.api.service.UserMarkService.TargetType;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.common.web.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserMarkController {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserMarkService markService;

    public UserMarkController(UserMarkService markService) {
        this.markService = markService;
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResult<UserTargetItem>> favorites(@RequestParam(defaultValue = "1") int targetType,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(list(Kind.FAVORITE, targetType, page, pageSize));
    }

    @PostMapping("/favorites")
    public ApiResponse<Void> addFavorite(@Valid @RequestBody MarkRequest request) {
        markService.add(UserContext.requireUserId(), Kind.FAVORITE,
                TargetType.of(request.targetType()), request.targetId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/favorites")
    public ApiResponse<Void> removeFavorite(@RequestParam int targetType, @RequestParam long targetId) {
        markService.remove(UserContext.requireUserId(), Kind.FAVORITE,
                TargetType.of(targetType), targetId);
        return ApiResponse.ok();
    }

    @GetMapping("/likes")
    public ApiResponse<PageResult<UserTargetItem>> likes(@RequestParam(defaultValue = "1") int targetType,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(list(Kind.LIKE, targetType, page, pageSize));
    }

    @PostMapping("/likes")
    public ApiResponse<Void> addLike(@Valid @RequestBody MarkRequest request) {
        markService.add(UserContext.requireUserId(), Kind.LIKE,
                TargetType.of(request.targetType()), request.targetId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/likes")
    public ApiResponse<Void> removeLike(@RequestParam int targetType, @RequestParam long targetId) {
        markService.remove(UserContext.requireUserId(), Kind.LIKE,
                TargetType.of(targetType), targetId);
        return ApiResponse.ok();
    }

    private PageResult<UserTargetItem> list(Kind kind, int targetType, int page, int pageSize) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return markService.list(UserContext.requireUserId(), kind,
                TargetType.of(targetType), page, safeSize);
    }
}
