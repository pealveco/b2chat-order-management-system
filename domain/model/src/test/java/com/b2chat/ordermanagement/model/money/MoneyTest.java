package com.b2chat.ordermanagement.model.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test
    void shouldCreateMoneyWhenAmountIsPositive() {
        var money = new Money(new BigDecimal("1200.50"));

        assertEquals(new BigDecimal("1200.50"), money.getAmount());
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThrows(InvalidMoneyException.class, () -> new Money(BigDecimal.ZERO));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(InvalidMoneyException.class, () -> new Money(new BigDecimal("-1")));
    }
}
