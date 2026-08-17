package com.b2chat.ordermanagement.r2dbc;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.r2dbc.product.ProductData;
import com.b2chat.ordermanagement.r2dbc.product.ProductReactiveRepository;
import com.b2chat.ordermanagement.r2dbc.product.ProductReactiveRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReactiveRepositoryAdapterTest {
    @InjectMocks
    ProductReactiveRepositoryAdapter repositoryAdapter;

    @Mock
    ProductReactiveRepository repository;

    @Mock
    ObjectMapper mapper;

    @Test
    void shouldSaveProduct() {
        var id = UUID.randomUUID();
        var product = Product.create("Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), 10);
        when(repository.save(any(ProductData.class))).thenReturn(Mono.just(new ProductData(
                id,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("25.50"),
                10
        )));

        StepVerifier.create(repositoryAdapter.save(product))
                .expectNextMatches(saved -> saved.getId().equals(id)
                        && saved.getName().equals("Keyboard")
                        && saved.getPrice().getAmount().equals(new BigDecimal("25.50"))
                        && saved.getStock().equals(10))
                .verifyComplete();
    }

    @Test
    void shouldMapPersistenceFailuresWhenSavingProduct() {
        var product = Product.create("Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), 10);
        when(repository.save(any(ProductData.class)))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(repositoryAdapter.save(product))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }

    @Test
    void shouldFindAllProducts() {
        var id = UUID.randomUUID();
        when(repository.findAll()).thenReturn(Flux.just(new ProductData(
                id,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("25.50"),
                10
        )));

        StepVerifier.create(repositoryAdapter.findAll())
                .expectNextMatches(product -> product.getId().equals(id)
                        && product.getName().equals("Keyboard")
                        && product.getPrice().getAmount().equals(new BigDecimal("25.50"))
                        && product.getStock().equals(10))
                .verifyComplete();
    }

    @Test
    void shouldMapPersistenceFailuresWhenFindingAllProducts() {
        when(repository.findAll())
                .thenReturn(Flux.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(repositoryAdapter.findAll())
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }
}
