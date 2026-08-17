package com.b2chat.ordermanagement.model.product;

import com.b2chat.ordermanagement.model.common.DomainException;

import java.util.UUID;

public class ProductNotFoundException extends DomainException {
    public ProductNotFoundException(UUID id) {
        super("PRODUCT_NOT_FOUND", "Product with id " + id + " was not found");
    }
}
