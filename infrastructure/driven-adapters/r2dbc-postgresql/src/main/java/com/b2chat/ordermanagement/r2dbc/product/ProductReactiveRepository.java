package com.b2chat.ordermanagement.r2dbc.product;

import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ProductReactiveRepository extends ReactiveCrudRepository<ProductData, UUID>,
        ReactiveQueryByExampleExecutor<ProductData> {
    Flux<ProductData> findByActiveTrue();
}
