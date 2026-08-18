package com.b2chat.ordermanagement.model.order;

import java.time.Instant;
import java.util.UUID;

public record OrderPlacedEvent(UUID orderId, UUID userId, Instant occurredAt) {
}
