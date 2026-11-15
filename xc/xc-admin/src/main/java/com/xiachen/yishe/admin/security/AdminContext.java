package com.xiachen.yishe.admin.security;

public final class AdminContext {

    private static final ThreadLocal<StaffPrincipal> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(StaffPrincipal principal) {
        HOLDER.set(principal);
    }

    public static StaffPrincipal get() {
        return HOLDER.get();
    }

    public static Long staffId() {
        StaffPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
