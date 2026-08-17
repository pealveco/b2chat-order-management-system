package com.b2chat.ordermanagement.redis.product;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import org.junit.jupiter.api.Test;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ProductCacheAdapterTest {
    private final ReactiveRedisConnectionFactory connectionFactory = mock(ReactiveRedisConnectionFactory.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);

    @Test
    void shouldWriteProductWithExpectedKey() {
        var id = UUID.randomUUID();
        var product = new Product(id, "Keyboard", "Mechanical keyboard",
                new Money(new BigDecimal("25.50")), 10);
        var adapter = spy(new ProductCacheAdapter(connectionFactory, objectMapper));

        doReturn(Mono.just(product)).when(adapter).saveProduct(same(product));
        doReturn(Mono.just(1L)).when(adapter).indexProduct(same(product));

        StepVerifier.create(adapter.put(product))
                .verifyComplete();

        verify(adapter).saveProduct(product);
        verify(adapter).indexProduct(product);
    }

    @Test
    void shouldMapCacheFailuresWhenWritingProduct() {
        var id = UUID.randomUUID();
        var product = new Product(id, "Keyboard", "Mechanical keyboard",
                new Money(new BigDecimal("25.50")), 10);
        var adapter = spy(new ProductCacheAdapter(connectionFactory, objectMapper));

        doReturn(Mono.error(new DataAccessResourceFailureException("redis unavailable")))
                .when(adapter).saveProduct(same(product));

        StepVerifier.create(adapter.put(product))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }

    @Test
    void shouldGetAllProductsFromCacheWhenIndexAndEntriesExist() {
        var id = UUID.randomUUID();
        var product = new Product(id, "Keyboard", "Mechanical keyboard",
                new Money(new BigDecimal("25.50")), 10);
        var adapter = spy(new ProductCacheAdapter(connectionFactory, objectMapper));

        doReturn(reactor.core.publisher.Flux.fromIterable(List.of(id.toString())))
                .when(adapter).findCachedProductIds();
        doReturn(Mono.just(product)).when(adapter).findCachedProduct(eq(id.toString()));

        StepVerifier.create(adapter.getAll())
                .expectNext(product)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenCacheIndexIsEmpty() {
        var adapter = spy(new ProductCacheAdapter(connectionFactory, objectMapper));

        doReturn(reactor.core.publisher.Flux.empty()).when(adapter).findCachedProductIds();

        StepVerifier.create(adapter.getAll())
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenCacheIndexHasMissingProductEntries() {
        var id = UUID.randomUUID();
        var adapter = spy(new ProductCacheAdapter(connectionFactory, objectMapper));

        doReturn(reactor.core.publisher.Flux.fromIterable(List.of(id.toString())))
                .when(adapter).findCachedProductIds();
        doReturn(Mono.empty()).when(adapter).findCachedProduct(eq(id.toString()));

        StepVerifier.create(adapter.getAll())
                .verifyComplete();
    }
}
