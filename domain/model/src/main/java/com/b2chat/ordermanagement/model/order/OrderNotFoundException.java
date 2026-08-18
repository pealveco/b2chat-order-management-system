package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.common.DomainException;

import java.util.UUID;

public class OrderNotFoundException extends DomainException {
    public OrderNotFoundException(UUID id) {
        super("ORDER_NOT_FOUND", "Order with id " + id + " was not found");
    }
}
