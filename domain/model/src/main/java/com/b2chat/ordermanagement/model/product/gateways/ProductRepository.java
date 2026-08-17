package com.b2chat.ordermanagement.model.product.gateways;

import com.b2chat.ordermanagement.model.product.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductRepository {
    Mono<Product> save(Product product);
    Mono<Product> findById(UUID id);
    Flux<Product> findAll();
    Mono<Boolean> decrementStockIfAvailable(UUID productId, int quantity);
}
