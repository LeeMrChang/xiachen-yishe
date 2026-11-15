package com.xiachen.yishe.common.cache;

import java.util.concurrent.ThreadLocalRandom;

public final class CacheTtl {

    public static final long HOME = 600;
    public static final long PRODUCT = 300;
    public static final long LIST = 60;
    public static final long NULL_VALUE = 60;

    private CacheTtl() {
    }

    /** ±20% 抖动，避免同一时刻批量 key 同时过期 */
    public static long jitter(long ttlSeconds) {
        double factor = 0.8 + ThreadLocalRandom.current().nextDouble(0.4);
        return Math.max(1, Math.round(ttlSeconds * factor));
    }
}
