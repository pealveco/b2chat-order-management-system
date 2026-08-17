package com.b2chat.ordermanagement.usecase.placeorder;

import java.util.UUID;

public record PlaceOrderItemCommand(UUID productId, Integer quantity) {
}
