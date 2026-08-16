package com.b2chat.ordermanagement.usecase.createproduct;

import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class CreateProductUseCase {
    private final ProductRepository productRepository;
    private final ProductCachePort productCachePort;

    public Mono<Product> execute(String name, String description, BigDecimal price, Integer stock) {
        return Mono.defer(() -> {
            var product = Product.create(name, description, new Money(price), stock);
            return productRepository.save(product)
                    .flatMap(savedProduct -> productCachePort.put(savedProduct).thenReturn(savedProduct));
        });
    }
}
