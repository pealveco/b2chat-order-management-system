package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.common.DomainException;

import java.util.UUID;

public class InsufficientStockException extends DomainException {
    public InsufficientStockException(UUID productId, int quantity) {
        super("INSUFFICIENT_STOCK",
                "Product " + productId + " does not have enough stock for quantity " + quantity);
    }
}
