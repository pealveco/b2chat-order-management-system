package com.b2chat.ordermanagement.usecase.listproducts;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListProductsUseCaseTest {
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private ListProductsUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        useCase = new ListProductsUseCase(productRepository, productCachePort);
    }

    @Test
    void shouldReturnProductsFromCacheWhenAvailable() {
        var product = product("Keyboard");
        when(productCachePort.getAll()).thenReturn(Flux.just(product));

        StepVerifier.create(useCase.execute())
                .expectNext(product)
                .verifyComplete();

        verify(productRepository, never()).findAll();
        verify(productCachePort, never()).putAll(Mockito.anyList());
    }

    @Test
    void shouldReadFromRepositoryAndPopulateCacheWhenCacheIsEmpty() {
        var products = List.of(product("Keyboard"), product("Mouse"));
        when(productCachePort.getAll()).thenReturn(Flux.empty());
        when(productRepository.findAll()).thenReturn(Flux.fromIterable(products));
        when(productCachePort.putAll(products)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute())
                .expectNextSequence(products)
                .verifyComplete();

        verify(productRepository).findAll();
        verify(productCachePort).putAll(products);
    }

    @Test
    void shouldReturnEmptyListWhenRepositoryHasNoProducts() {
        when(productCachePort.getAll()).thenReturn(Flux.empty());
        when(productRepository.findAll()).thenReturn(Flux.empty());
        when(productCachePort.putAll(List.of())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute())
                .verifyComplete();
    }

    @Test
    void shouldFallbackToRepositoryWhenCacheReadFails() {
        var products = List.of(product("Keyboard"));
        when(productCachePort.getAll())
                .thenReturn(Flux.error(new RepositoryUnavailableException("Product cache is temporarily unavailable")));
        when(productRepository.findAll()).thenReturn(Flux.fromIterable(products));
        when(productCachePort.putAll(products)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute())
                .expectNextSequence(products)
                .verifyComplete();
    }

    @Test
    void shouldReturnRepositoryProductsWhenCachePopulationFails() {
        var products = List.of(product("Keyboard"));
        when(productCachePort.getAll()).thenReturn(Flux.empty());
        when(productRepository.findAll()).thenReturn(Flux.fromIterable(products));
        when(productCachePort.putAll(products))
                .thenReturn(Mono.error(new RepositoryUnavailableException("Product cache is temporarily unavailable")));

        StepVerifier.create(useCase.execute())
                .expectNextSequence(products)
                .verifyComplete();
    }

    private Product product(String name) {
        return new Product(UUID.randomUUID(), name, "Description", new Money(new BigDecimal("25.50")), 10);
    }
}
