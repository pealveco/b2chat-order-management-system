package com.b2chat.ordermanagement.model.money;

import com.b2chat.ordermanagement.model.common.DomainException;

public class InvalidMoneyException extends DomainException {
    public InvalidMoneyException() {
        super("INVALID_MONEY", "price must be greater than zero");
    }
}
