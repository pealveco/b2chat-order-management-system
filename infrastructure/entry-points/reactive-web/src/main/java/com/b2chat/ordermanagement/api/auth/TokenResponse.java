package com.b2chat.ordermanagement.api.auth;

public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
    public static TokenResponse bearer(String token, long expiresInSeconds) {
        return new TokenResponse(token, "Bearer", expiresInSeconds);
    }
}
