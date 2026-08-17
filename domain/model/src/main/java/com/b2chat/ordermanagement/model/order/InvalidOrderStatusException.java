package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.common.DomainException;

public class InvalidOrderStatusException extends DomainException {
    public InvalidOrderStatusException(String status) {
        super("INVALID_ORDER_STATUS", "Order status " + status + " is not supported");
    }
}
