package com.b2chat.ordermanagement.model.common.gateways;

import reactor.core.publisher.Mono;

public interface TransactionPort {
    <T> Mono<T> transactional(Mono<T> publisher);
}
