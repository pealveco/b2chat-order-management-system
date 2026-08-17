package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.common.DomainException;

public class EmptyOrderItemsException extends DomainException {
    public EmptyOrderItemsException() {
        super("EMPTY_ORDER_ITEMS", "Order must contain at least one item");
    }
}
