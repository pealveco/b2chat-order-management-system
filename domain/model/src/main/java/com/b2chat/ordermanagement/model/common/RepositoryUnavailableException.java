package com.b2chat.ordermanagement.model.common;

public class RepositoryUnavailableException extends RuntimeException {
    public RepositoryUnavailableException(String message) {
        super(message);
    }

    public RepositoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
