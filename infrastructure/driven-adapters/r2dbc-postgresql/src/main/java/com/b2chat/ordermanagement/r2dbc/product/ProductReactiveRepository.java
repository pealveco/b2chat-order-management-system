package com.b2chat.ordermanagement.r2dbc.product;

import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductReactiveRepository extends ReactiveCrudRepository<ProductData, UUID>,
        ReactiveQueryByExampleExecutor<ProductData> {
    Flux<ProductData> findByActiveTrue();

    @Modifying
    @Query("""
            UPDATE products
            SET stock = stock - :quantity
            WHERE id = :productId
              AND active = TRUE
              AND stock >= :quantity
            """)
    Mono<Integer> decrementStockIfAvailable(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
