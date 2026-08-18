package com.b2chat.ordermanagement.usecase.createproduct;

import com.b2chat.ordermanagement.model.money.InvalidMoneyException;
import com.b2chat.ordermanagement.model.product.InvalidProductStockException;
import com.b2chat.ordermanagement.model.product.Product;
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

class CreateProductUseCaseTest {
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private CreateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        useCase = new CreateProductUseCase(productRepository, productCachePort);
    }

    @Test
    void shouldCreateProductAndWriteThroughCache() {
        var id = new UUID(1L, 1L);
        when(productRepository.save(any())).thenAnswer(invocation -> {
            var product = invocation.getArgument(0, Product.class);
            return Mono.just(new Product(id, product.getName(), product.getDescription(),
                    product.getPrice(), product.getStock()));
        });
        when(productCachePort.put(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("Keyboard", "Mechanical keyboard", new BigDecimal("25.50"), 10))
                .expectNextMatches(product -> product.getId().equals(id)
                        && product.getName().equals("Keyboard")
                        && product.getPrice().getAmount().equals(new BigDecimal("25.50"))
                        && product.getStock().equals(10))
                .verifyComplete();

        verify(productRepository).save(any());
        verify(productCachePort).put(any());
    }

    @Test
    void shouldFailWhenPriceIsInvalidBeforeSaving() {
        StepVerifier.create(useCase.execute("Keyboard", "Mechanical keyboard", BigDecimal.ZERO, 10))
                .expectError(InvalidMoneyException.class)
                .verify();

        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldFailWhenStockIsInvalidBeforeSaving() {
        StepVerifier.create(useCase.execute("Keyboard", "Mechanical keyboard", new BigDecimal("25.50"), -1))
                .expectError(InvalidProductStockException.class)
                .verify();

        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }
}
