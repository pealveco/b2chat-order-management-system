package com.b2chat.ordermanagement.model.common;

public class RequiredFieldException extends DomainException {
    public RequiredFieldException(String field) {
        super("REQUIRED_FIELD", field + " is required and cannot be blank");
    }
}
