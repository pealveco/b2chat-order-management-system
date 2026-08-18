package com.b2chat.ordermanagement.model.user;

import com.b2chat.ordermanagement.model.common.DomainException;

public class EmailAlreadyExistsException extends DomainException {
    public EmailAlreadyExistsException(String email) {
        super("EMAIL_ALREADY_EXISTS", "A user with email " + email + " already exists");
    }
}
