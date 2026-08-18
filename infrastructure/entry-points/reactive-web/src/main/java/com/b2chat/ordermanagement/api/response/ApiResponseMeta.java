package com.b2chat.ordermanagement.api.response;

import java.time.Instant;

public record ApiResponseMeta(String path, Instant timestamp) {
}
