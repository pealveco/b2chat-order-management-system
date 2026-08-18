package com.b2chat.ordermanagement.api.auth;

import jakarta.validation.constraints.Email;

import java.util.UUID;

public record IssueTokenRequest(
        UUID userId,
        @Email(message = "email must have a valid format")
        String email
) {
}
