package com.b2chat.ordermanagement.model.order;

public enum OrderStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    CANCELLED;

    public static OrderStatus from(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (RuntimeException error) {
            throw new InvalidOrderStatusException(status);
        }
    }

    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
