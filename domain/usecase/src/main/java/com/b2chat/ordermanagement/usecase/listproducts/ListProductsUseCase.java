package com.b2chat.ordermanagement.usecase.listproducts;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
public class ListProductsUseCase {
    private final ProductRepository productRepository;
    private final ProductCachePort productCachePort;

    public Flux<Product> execute() {
        return productCachePort.getAll()
                .collectList()
                .onErrorResume(RepositoryUnavailableException.class, error -> Mono.just(List.of()))
                .flatMapMany(cachedProducts -> cachedProducts.isEmpty()
                        ? findAllFromRepositoryAndPopulateCache()
                        : Flux.fromIterable(cachedProducts));
    }

    private Flux<Product> findAllFromRepositoryAndPopulateCache() {
        return productRepository.findAll()
                .collectList()
                .flatMapMany(products -> productCachePort.putAll(products)
                        .onErrorResume(RepositoryUnavailableException.class, error -> Mono.empty())
                        .thenMany(Flux.fromIterable(products)));
    }
}
