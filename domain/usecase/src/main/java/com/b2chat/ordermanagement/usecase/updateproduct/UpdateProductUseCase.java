package com.b2chat.ordermanagement.usecase.updateproduct;

import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.ProductNotFoundException;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@RequiredArgsConstructor
public class UpdateProductUseCase {
    private final ProductRepository productRepository;
    private final ProductCachePort productCachePort;

    public Mono<Product> execute(UUID id, String name, String description, BigDecimal price, Integer stock) {
        return Mono.defer(() -> {
            var product = new Product(id, name, description, new Money(price), stock);
            return productRepository.findById(id)
                    .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                    .flatMap(existingProduct -> productRepository.save(product))
                    .flatMap(saved -> productCachePort.put(saved).thenReturn(saved));
        });
    }
}
