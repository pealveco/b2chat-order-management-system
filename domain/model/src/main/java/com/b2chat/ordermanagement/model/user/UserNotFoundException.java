package com.b2chat.ordermanagement.model.user;

import com.b2chat.ordermanagement.model.common.DomainException;

import java.util.UUID;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(UUID id) {
        super("USER_NOT_FOUND", "User with id " + id + " was not found");
    }

    public UserNotFoundException(String email) {
        super("USER_NOT_FOUND", "User with email " + email + " was not found");
    }
}
