package com.b2chat.ordermanagement.model.user;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.email.Email;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class User {
    private final UUID id;
    private final Email email;
    private final String name;
    private final String address;

    public User(UUID id, Email email, String name, String address) {
        this.id = id;
        this.email = email;
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
    }

    public static User create(Email email, String name, String address) {
        return new User(null, email, name, address);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new RequiredFieldException(field);
        }
        return value.trim();
    }
}
