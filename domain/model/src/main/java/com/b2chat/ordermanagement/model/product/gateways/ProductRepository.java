package com.b2chat.ordermanagement.model.product.gateways;

import com.b2chat.ordermanagement.model.product.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepository {
    Mono<Product> save(Product product);
    Flux<Product> findAll();
}
