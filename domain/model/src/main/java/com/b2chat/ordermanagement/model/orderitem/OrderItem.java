package com.b2chat.ordermanagement.model.orderitem;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.money.InvalidMoneyException;
import com.b2chat.ordermanagement.model.money.Money;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class OrderItem {
    private final UUID productId;
    private final Integer quantity;
    private final Money unitPriceAtOrderTime;

    public OrderItem(UUID productId, Integer quantity, Money unitPriceAtOrderTime) {
        this.productId = requireProductId(productId);
        this.quantity = validateQuantity(quantity);
        this.unitPriceAtOrderTime = requirePrice(unitPriceAtOrderTime);
    }

    private static UUID requireProductId(UUID productId) {
        if (productId == null) {
            throw new RequiredFieldException("productId");
        }
        return productId;
    }

    private static Integer validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidOrderItemQuantityException();
        }
        return quantity;
    }

    private static Money requirePrice(Money unitPriceAtOrderTime) {
        if (unitPriceAtOrderTime == null) {
            throw new InvalidMoneyException();
        }
        return unitPriceAtOrderTime;
    }
}
