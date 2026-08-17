package com.b2chat.ordermanagement.usecase.deleteproduct;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.ProductNotFoundException;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteProductUseCaseTest {
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private DeleteProductUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        useCase = new DeleteProductUseCase(productRepository, productCachePort);
    }

    @Test
    void shouldSoftDeleteProductAndEvictCache() {
        var id = new UUID(1L, 1L);
        var product = product(id);
        when(productRepository.findById(id)).thenReturn(Mono.just(product));
        when(productRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Product.class)));
        when(productCachePort.evict(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .verifyComplete();

        verify(productRepository).findById(id);
        verify(productRepository).save(Mockito.argThat(saved -> !saved.getActive()));
        verify(productCachePort).evict(id);
    }

    @Test
    void shouldFailWhenProductDoesNotExist() {
        var id = new UUID(1L, 1L);
        when(productRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .expectError(ProductNotFoundException.class)
                .verify();

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).evict(any());
    }

    @Test
    void shouldNotEvictCacheWhenPersistenceFails() {
        var id = new UUID(1L, 1L);
        when(productRepository.findById(id)).thenReturn(Mono.just(product(id)));
        when(productRepository.save(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException(
                        "Product repository is temporarily unavailable")));

        StepVerifier.create(useCase.execute(id))
                .expectError(RepositoryUnavailableException.class)
                .verify();

        verify(productRepository).findById(id);
        verify(productRepository).save(any());
        verify(productCachePort, never()).evict(any());
    }

    private Product product(UUID id) {
        return new Product(id, "Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), 10);
    }
}
