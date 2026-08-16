package com.b2chat.ordermanagement.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank(message = "email is required and cannot be blank")
        @Email(message = "email format is invalid")
        String email,
        @NotBlank(message = "name is required and cannot be blank")
        String name,
        @NotBlank(message = "address is required and cannot be blank")
        String address
) {
}
