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

        doReturn(Mono.just(product)).when(adapter).save(eq("product:" + id), same(product));

        StepVerifier.create(adapter.put(product))
                .verifyComplete();

        verify(adapter).save("product:" + id, product);
    }

    @Test
    void shouldMapCacheFailuresWhenWritingProduct() {
        var id = UUID.randomUUID();
        var product = new Product(id, "Keyboard", "Mechanical keyboard",
                new Money(new BigDecimal("25.50")), 10);
        var adapter = spy(new ProductCacheAdapter(connectionFactory, objectMapper));

        doReturn(Mono.error(new DataAccessResourceFailureException("redis unavailable")))
                .when(adapter).save(eq("product:" + id), same(product));

        StepVerifier.create(adapter.put(product))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }
}
