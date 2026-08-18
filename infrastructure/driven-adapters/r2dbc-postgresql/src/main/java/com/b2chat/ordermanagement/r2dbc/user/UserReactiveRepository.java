package com.b2chat.ordermanagement.r2dbc.user;

import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserReactiveRepository extends ReactiveCrudRepository<UserData, UUID>, ReactiveQueryByExampleExecutor<UserData> {
    Mono<Boolean> existsByEmail(String email);

    Mono<UserData> findByEmail(String email);
}
