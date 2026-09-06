package com.chenglei.miniprogram.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST("COMMON_400", "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("AUTH_401", "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH_403", "无权执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND("COMMON_404", "请求的资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT("COMMON_409", "当前操作与资源状态冲突", HttpStatus.CONFLICT),
    INTERNAL_ERROR("COMMON_500", "服务暂时不可用", HttpStatus.INTERNAL_SERVER_ERROR),
    WECHAT_502("WECHAT_502", "微信接口调用失败", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }
}
