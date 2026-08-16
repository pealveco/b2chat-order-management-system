package com.b2chat.ordermanagement.model.product.gateways;

import com.b2chat.ordermanagement.model.product.Product;
import reactor.core.publisher.Mono;

public interface ProductCachePort {
    Mono<Void> put(Product product);
}
