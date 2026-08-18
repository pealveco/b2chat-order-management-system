package com.b2chat.ordermanagement.api.user;

import com.b2chat.ordermanagement.model.user.User;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name, String address) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail().getValue(),
                user.getName(),
                user.getAddress()
        );
    }
}
