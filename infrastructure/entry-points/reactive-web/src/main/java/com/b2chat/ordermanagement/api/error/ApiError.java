package com.b2chat.ordermanagement.api.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        List<ApiErrorDetail> details
) {
    public static ApiError of(String code, String message, int status, String path) {
        return new ApiError(code, message, status, path, Instant.now(), List.of());
    }

    public static ApiError of(String code, String message, int status, String path, List<ApiErrorDetail> details) {
        return new ApiError(code, message, status, path, Instant.now(), details);
    }
}
