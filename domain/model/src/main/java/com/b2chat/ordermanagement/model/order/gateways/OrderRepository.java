package com.b2chat.ordermanagement.model.order.gateways;

import com.b2chat.ordermanagement.model.order.Order;
import reactor.core.publisher.Mono;

public interface OrderRepository {
    Mono<Order> save(Order order);
}
