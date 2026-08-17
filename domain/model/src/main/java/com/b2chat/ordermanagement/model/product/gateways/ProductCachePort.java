package com.b2chat.ordermanagement.model.product.gateways;

import com.b2chat.ordermanagement.model.product.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductCachePort {
    Mono<Void> put(Product product);
    Flux<Product> getAll();
    Mono<Void> putAll(List<Product> products);
}
