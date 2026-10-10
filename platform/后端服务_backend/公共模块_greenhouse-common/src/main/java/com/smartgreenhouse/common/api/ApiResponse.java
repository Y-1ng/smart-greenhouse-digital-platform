package com.smartgreenhouse.common.api;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "OK", data, null);
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("0", "OK", data, requestId);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }
}

