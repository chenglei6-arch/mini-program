package com.chenglei.miniprogram.common.api;

import java.time.Instant;
import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, Instant timestamp, String requestId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, Instant.now(), MDC.get("requestId"));
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now(), MDC.get("requestId"));
    }
}
