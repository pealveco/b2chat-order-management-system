package com.b2chat.ordermanagement.r2dbc.order;

import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrderReactiveRepository extends ReactiveCrudRepository<OrderData, UUID> {
    @Modifying
    @Query("""
            UPDATE orders
            SET status = :status
            WHERE id = :id
            """)
    Mono<Integer> updateStatus(@Param("id") UUID id, @Param("status") String status);
}
