package com.b2chat.ordermanagement.model.product;

import com.b2chat.ordermanagement.model.common.DomainException;

public class InvalidProductStockException extends DomainException {
    public InvalidProductStockException() {
        super("INVALID_PRODUCT_STOCK", "stock must be greater than or equal to zero");
    }
}
