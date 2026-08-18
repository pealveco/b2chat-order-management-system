package com.b2chat.ordermanagement.model.orderitem;

import com.b2chat.ordermanagement.model.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {
    @Test
    void shouldCreateOrderItem() {
        var productId = UUID.randomUUID();

        var item = new OrderItem(productId, 2, new Money(new BigDecimal("25.50")));

        assertEquals(productId, item.getProductId());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("25.50"), item.getUnitPriceAtOrderTime().getAmount());
    }

    @Test
    void shouldRejectInvalidQuantity() {
        assertThrows(InvalidOrderItemQuantityException.class,
                () -> new OrderItem(UUID.randomUUID(), 0, new Money(new BigDecimal("25.50"))));
    }
}
