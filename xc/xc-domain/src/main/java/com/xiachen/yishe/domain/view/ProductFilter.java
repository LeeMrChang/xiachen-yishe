package com.xiachen.yishe.domain.view;

public record ProductFilter(
        Long storeId,
        String category,
        String color,
        String size,
        String material,
        String keyword,
        boolean onlyStock,
        String sort
) {
}
