package com.b2chat.ordermanagement.api.order;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(
        @NotBlank(message = "status is required")
        String status
) {
}
