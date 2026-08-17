package com.b2chat.ordermanagement.model.product.gateways;

import com.b2chat.ordermanagement.model.product.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ProductCachePort {
    Mono<Void> put(Product product);
    Mono<Void> evict(UUID productId);
    Flux<Product> getAll();
    Mono<Void> putAll(List<Product> products);
}
