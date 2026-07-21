package com.lhj.jizhang.common.api;

import org.slf4j.MDC;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant serverTime
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, MDC.get("traceId"), Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"), Instant.now());
    }
}
