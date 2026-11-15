package com.xiachen.yishe.api.dto;

import java.util.List;

public record TryonGroup(
        Long storeId,
        String storeName,
        String storeAddress,
        List<TryonItemView> items
) {
}
