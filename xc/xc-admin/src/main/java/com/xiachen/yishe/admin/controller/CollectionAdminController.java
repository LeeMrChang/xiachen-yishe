package com.xiachen.yishe.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.admin.dto.CollectionOption;
import com.xiachen.yishe.common.web.ApiResponse;
import com.xiachen.yishe.domain.entity.Collection;
import com.xiachen.yishe.domain.mapper.CollectionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/collections")
public class CollectionAdminController {

    private final CollectionMapper collectionMapper;

    public CollectionAdminController(CollectionMapper collectionMapper) {
        this.collectionMapper = collectionMapper;
    }

    @GetMapping
    public ApiResponse<List<CollectionOption>> list() {
        List<CollectionOption> options = collectionMapper.selectList(Wrappers.<Collection>lambdaQuery()
                        .ne(Collection::getStatus, "OFF")
                        .orderByAsc(Collection::getSortNo)
                        .orderByAsc(Collection::getId))
                .stream()
                .map(c -> new CollectionOption(c.getId(), c.getName(), c.getStatus()))
                .toList();
        return ApiResponse.ok(options);
    }
}
