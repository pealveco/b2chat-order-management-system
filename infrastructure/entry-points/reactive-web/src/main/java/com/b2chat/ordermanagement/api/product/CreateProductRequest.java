package com.b2chat.ordermanagement.api.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "name is required and cannot be blank")
        String name,
        String description,
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than zero")
        BigDecimal price,
        @NotNull(message = "stock is required")
        @PositiveOrZero(message = "stock must be greater than or equal to zero")
        Integer stock
) {
}
