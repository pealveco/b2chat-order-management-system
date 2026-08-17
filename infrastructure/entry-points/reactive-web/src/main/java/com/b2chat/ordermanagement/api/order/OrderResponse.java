package com.b2chat.ordermanagement.api.order;

import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String status,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }

    public record OrderItemResponse(UUID productId, Integer quantity, BigDecimal unitPriceAtOrderTime) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitPriceAtOrderTime().getAmount());
        }
    }
}
