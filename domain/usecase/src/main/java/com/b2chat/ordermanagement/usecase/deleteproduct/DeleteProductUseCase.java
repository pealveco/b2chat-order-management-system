package com.b2chat.ordermanagement.usecase.deleteproduct;

import com.b2chat.ordermanagement.model.product.ProductNotFoundException;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class DeleteProductUseCase {
    private final ProductRepository productRepository;
    private final ProductCachePort productCachePort;

    public Mono<Void> execute(UUID id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .map(product -> product.deactivate())
                .flatMap(productRepository::save)
                .flatMap(product -> productCachePort.evict(id));
    }
}
