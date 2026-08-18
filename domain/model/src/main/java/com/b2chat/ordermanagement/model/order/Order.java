package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public final class Order {
    private final UUID id;
    private final UUID userId;
    private final List<OrderItem> items;
    private final OrderStatus status;
    private final Instant createdAt;

    public Order(UUID id, UUID userId, List<OrderItem> items, OrderStatus status, Instant createdAt) {
        this.id = id;
        this.userId = requireUserId(userId);
        this.items = requireItems(items);
        this.status = status == null ? OrderStatus.PENDING : status;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static Order pending(UUID userId, List<OrderItem> items) {
        return new Order(null, userId, items, OrderStatus.PENDING, Instant.now());
    }

    public Order withId(UUID id) {
        return new Order(id, userId, items, status, createdAt);
    }

    public Order withStatus(OrderStatus status) {
        return new Order(id, userId, items, status, createdAt);
    }

    private static UUID requireUserId(UUID userId) {
        if (userId == null) {
            throw new RequiredFieldException("userId");
        }
        return userId;
    }

    private static List<OrderItem> requireItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderItemsException();
        }
        return List.copyOf(items);
    }
}
