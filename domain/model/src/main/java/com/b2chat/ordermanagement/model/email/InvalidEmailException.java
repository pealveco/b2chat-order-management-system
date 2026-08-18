package com.b2chat.ordermanagement.model.email;

import com.b2chat.ordermanagement.model.common.DomainException;

public class InvalidEmailException extends DomainException {
    public InvalidEmailException() {
        super("INVALID_EMAIL", "Email format is invalid");
    }
}
