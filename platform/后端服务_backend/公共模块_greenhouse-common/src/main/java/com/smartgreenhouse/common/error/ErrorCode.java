package com.smartgreenhouse.common.error;

public enum ErrorCode {
    BAD_REQUEST("40000", "请求参数不正确"),
    UNAUTHORIZED("40100", "未认证或登录已过期"),
    FORBIDDEN("40300", "没有执行该操作的权限"),
    NOT_FOUND("40400", "请求的资源不存在"),
    CONFLICT("40900", "业务状态冲突"),
    CHAIN_UNAVAILABLE("50301", "区块链服务暂不可用"),
    DEVICE_OFFLINE("50302", "目标设备离线"),
    INTERNAL_ERROR("50000", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}

