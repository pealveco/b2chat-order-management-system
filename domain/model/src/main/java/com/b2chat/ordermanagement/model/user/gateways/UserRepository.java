package com.b2chat.ordermanagement.model.user.gateways;

import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.User;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepository {
    Mono<User> save(User user);

    Mono<User> findById(UUID id);

    Mono<User> findByEmail(Email email);

    Mono<Boolean> existsByEmail(Email email);
}
