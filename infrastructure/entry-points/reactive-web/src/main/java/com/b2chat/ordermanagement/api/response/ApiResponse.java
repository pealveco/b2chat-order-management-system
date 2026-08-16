package com.b2chat.ordermanagement.api.response;

import java.time.Instant;

public record ApiResponse<T>(T data, ApiResponseMeta meta) {
    public static <T> ApiResponse<T> success(T data, String path) {
        return new ApiResponse<>(data, new ApiResponseMeta(path, Instant.now()));
    }
}
