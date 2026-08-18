package com.b2chat.ordermanagement.usecase.getorder;

import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.OrderNotFoundException;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetOrderUseCase {
    private final OrderRepository orderRepository;

    public Mono<Order> execute(UUID id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(id)));
    }
}
