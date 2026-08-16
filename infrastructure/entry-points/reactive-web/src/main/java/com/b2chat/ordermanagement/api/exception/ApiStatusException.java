package com.b2chat.ordermanagement.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiStatusException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApiStatusException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
