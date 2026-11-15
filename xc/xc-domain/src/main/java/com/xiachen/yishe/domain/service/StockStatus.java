package com.xiachen.yishe.domain.service;

public enum StockStatus {
    OUT,
    LOW,
    IN_STOCK;

    public static StockStatus of(long quantity) {
        if (quantity <= 0) {
            return OUT;
        }
        return quantity <= 2 ? LOW : IN_STOCK;
    }
}
