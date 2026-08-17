package com.b2chat.ordermanagement.r2dbc.order;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface OrderItemReactiveRepository extends ReactiveCrudRepository<OrderItemData, UUID> {
}
