package com.b2chat.ordermanagement.model.orderitem;

import com.b2chat.ordermanagement.model.common.DomainException;

public class InvalidOrderItemQuantityException extends DomainException {
    public InvalidOrderItemQuantityException() {
        super("INVALID_ORDER_ITEM_QUANTITY", "Order item quantity must be greater than zero");
    }
}
