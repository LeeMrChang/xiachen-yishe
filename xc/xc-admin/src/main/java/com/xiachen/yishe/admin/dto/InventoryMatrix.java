package com.xiachen.yishe.admin.dto;

import java.util.List;

public record InventoryMatrix(Long productId, String title, List<SkuCol> skus,
                              List<StoreCol> stores, List<Cell> cells) {

    public record SkuCol(Long id, String color, String size, String status) {
    }

    public record StoreCol(Long id, String name) {
    }

    public record Cell(Long skuId, Long storeId, int quantity, boolean onSale) {
    }
}
