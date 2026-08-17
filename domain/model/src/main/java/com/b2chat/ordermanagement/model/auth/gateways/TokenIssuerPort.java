package com.b2chat.ordermanagement.model.auth.gateways;

import com.b2chat.ordermanagement.model.user.User;
import reactor.core.publisher.Mono;

public interface TokenIssuerPort {
    Mono<String> issue(User user);
}
