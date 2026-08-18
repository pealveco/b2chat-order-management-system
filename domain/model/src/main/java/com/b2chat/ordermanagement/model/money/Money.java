package com.b2chat.ordermanagement.model.money;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public final class Money {
    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidMoneyException();
        }
        this.amount = amount;
    }
}
