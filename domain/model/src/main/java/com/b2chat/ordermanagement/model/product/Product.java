package com.b2chat.ordermanagement.model.product;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.money.InvalidMoneyException;
import com.b2chat.ordermanagement.model.money.Money;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class Product {
    private final UUID id;
    private final String name;
    private final String description;
    private final Money price;
    private final Integer stock;

    public Product(UUID id, String name, String description, Money price, Integer stock) {
        this.id = id;
        this.name = requireText(name, "name");
        this.description = description == null ? "" : description.trim();
        this.price = requirePrice(price);
        this.stock = validateStock(stock);
    }

    public static Product create(String name, String description, Money price, Integer stock) {
        return new Product(null, name, description, price, stock);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new RequiredFieldException(field);
        }
        return value.trim();
    }

    private static Integer validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new InvalidProductStockException();
        }
        return stock;
    }

    private static Money requirePrice(Money price) {
        if (price == null) {
            throw new InvalidMoneyException();
        }
        return price;
    }
}
