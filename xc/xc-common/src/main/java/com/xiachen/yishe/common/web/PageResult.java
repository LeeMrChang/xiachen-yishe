package com.xiachen.yishe.common.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PageResult<T>(List<T> list, long total, int page, int size) {

    @JsonProperty("hasMore")
    public boolean hasMore() {
        return (long) page * size < total;
    }
}
