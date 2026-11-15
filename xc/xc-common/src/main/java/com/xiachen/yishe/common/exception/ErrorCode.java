package com.xiachen.yishe.common.exception;

public enum ErrorCode {

    PARAM_ERROR(40001, "参数不正确"),
    UNAUTHORIZED(40101, "请先登录"),
    LOGIN_FAILED(40102, "账号或密码不正确"),
    ACCOUNT_DISABLED(40103, "账号已停用"),
    WX_LOGIN_FAILED(40104, "微信登录失败，请重试"),
    FORBIDDEN(40301, "没有权限"),
    NOT_FOUND(40401, "内容不存在"),
    SYSTEM_ERROR(50000, "系统开小差了，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
