package com.b2chat.ordermanagement.usecase.updateproduct;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.InvalidMoneyException;
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

class UpdateProductUseCaseTest {
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private UpdateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        useCase = new UpdateProductUseCase(productRepository, productCachePort);
    }

    @Test
    void shouldUpdateProductAndWriteThroughCache() {
        var id = new UUID(1L, 1L);
        var existingProduct = new Product(id, "Keyboard", "Mechanical keyboard",
                new Money(new BigDecimal("25.50")), 10);
        when(productRepository.findById(id)).thenReturn(Mono.just(existingProduct));
        when(productRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Product.class)));
        when(productCachePort.put(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id, "Mouse", "Wireless mouse", new BigDecimal("15.75"), 25))
                .expectNextMatches(product -> product.getId().equals(id)
                        && product.getName().equals("Mouse")
                        && product.getDescription().equals("Wireless mouse")
                        && product.getPrice().getAmount().equals(new BigDecimal("15.75"))
                        && product.getStock().equals(25))
                .verifyComplete();

        verify(productRepository).findById(id);
        verify(productRepository).save(any());
        verify(productCachePort).put(any());
    }

    @Test
    void shouldFailWhenProductDoesNotExist() {
        var id = new UUID(1L, 1L);
        when(productRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id, "Mouse", "Wireless mouse", new BigDecimal("15.75"), 25))
                .expectError(ProductNotFoundException.class)
                .verify();

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldNotUpdateCacheWhenPersistenceFails() {
        var id = new UUID(1L, 1L);
        var existingProduct = new Product(id, "Keyboard", "Mechanical keyboard",
                new Money(new BigDecimal("25.50")), 10);
        when(productRepository.findById(id)).thenReturn(Mono.just(existingProduct));
        when(productRepository.save(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException(
                        "Product repository is temporarily unavailable")));

        StepVerifier.create(useCase.execute(id, "Mouse", "Wireless mouse", new BigDecimal("15.75"), 25))
                .expectError(RepositoryUnavailableException.class)
                .verify();

        verify(productRepository).findById(id);
        verify(productRepository).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldFailWhenPriceIsInvalidBeforeQueryingRepository() {
        var id = new UUID(1L, 1L);

        StepVerifier.create(useCase.execute(id, "Mouse", "Wireless mouse", BigDecimal.ZERO, 25))
                .expectError(InvalidMoneyException.class)
                .verify();

        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }
}
