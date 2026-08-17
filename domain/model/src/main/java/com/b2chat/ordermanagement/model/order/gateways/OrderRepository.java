package com.b2chat.ordermanagement.model.order.gateways;

import com.b2chat.ordermanagement.model.order.Order;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrderRepository {
    Mono<Order> save(Order order);
    Mono<Order> findById(UUID id);
    Mono<Order> updateStatus(Order order);
}
