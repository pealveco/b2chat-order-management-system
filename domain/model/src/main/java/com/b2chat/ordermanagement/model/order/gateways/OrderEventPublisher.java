package com.b2chat.ordermanagement.model.order.gateways;

import com.b2chat.ordermanagement.model.order.OrderPlacedEvent;

public interface OrderEventPublisher {
    void publishOrderPlaced(OrderPlacedEvent event);
}
