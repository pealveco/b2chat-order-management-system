package com.b2chat.ordermanagement.api.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull(message = "userId is required")
        UUID userId,
        @NotEmpty(message = "items must contain at least one item")
        List<@Valid PlaceOrderItemRequest> items
) {
    public record PlaceOrderItemRequest(
            @NotNull(message = "productId is required")
            UUID productId,
            @NotNull(message = "quantity is required")
            @Positive(message = "quantity must be greater than zero")
            Integer quantity
    ) {
    }
}
