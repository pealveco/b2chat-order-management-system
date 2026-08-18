package com.b2chat.ordermanagement.api.exception;

import com.b2chat.ordermanagement.api.error.ApiErrorDetail;

import lombok.Getter;

import java.util.List;

@Getter
public class InvalidRequestException extends RuntimeException {
    private final String code;
    private final List<ApiErrorDetail> details;

    public InvalidRequestException(String code, String message, List<ApiErrorDetail> details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
