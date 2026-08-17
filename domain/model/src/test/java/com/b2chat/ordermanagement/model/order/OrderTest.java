package com.b2chat.ordermanagement.model.order;

import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {
    @Test
    void shouldCreatePendingOrder() {
        var userId = UUID.randomUUID();
        var item = new OrderItem(UUID.randomUUID(), 2, new Money(new BigDecimal("25.50")));

        var order = Order.pending(userId, List.of(item));

        assertEquals(userId, order.getUserId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(1, order.getItems().size());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void shouldRejectEmptyItems() {
        var userId = UUID.randomUUID();

        assertThrows(EmptyOrderItemsException.class, () -> Order.pending(userId, List.of()));
    }
}
