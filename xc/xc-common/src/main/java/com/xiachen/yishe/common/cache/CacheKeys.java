package com.xiachen.yishe.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CacheKeys {

    public static final String HOME_PREFIX = "xc:home:";
    public static final String PRODUCT_PREFIX = "xc:product:";
    public static final String LIST_PREFIX = "xc:list:";

    private CacheKeys() {
    }

    public static String home(Long storeId) {
        return HOME_PREFIX + (storeId == null ? 0 : storeId);
    }

    public static String product(Long id, Long storeId) {
        return PRODUCT_PREFIX + id + ":" + (storeId == null ? 0 : storeId);
    }

    public static String list(Long storeId, String params) {
        return LIST_PREFIX + (storeId == null ? 0 : storeId) + ":" + sha1(params);
    }

    public static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
