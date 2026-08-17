package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.common.DomainException;

public class InvalidOrderStatusTransitionException extends DomainException {
    public InvalidOrderStatusTransitionException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super("INVALID_ORDER_STATUS_TRANSITION",
                "Order status cannot transition from " + currentStatus + " to " + targetStatus);
    }
}
