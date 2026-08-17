package com.b2chat.ordermanagement.r2dbc;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import com.b2chat.ordermanagement.r2dbc.order.OrderData;
import com.b2chat.ordermanagement.r2dbc.order.OrderItemData;
import com.b2chat.ordermanagement.r2dbc.order.OrderItemReactiveRepository;
import com.b2chat.ordermanagement.r2dbc.order.OrderReactiveRepository;
import com.b2chat.ordermanagement.r2dbc.order.OrderReactiveRepositoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OrderReactiveRepositoryAdapterTest {
    private OrderReactiveRepository orderRepository;
    private OrderItemReactiveRepository orderItemRepository;
    private OrderReactiveRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderReactiveRepository.class);
        orderItemRepository = Mockito.mock(OrderItemReactiveRepository.class);
        adapter = new OrderReactiveRepositoryAdapter(orderRepository, orderItemRepository);
    }

    @Test
    void shouldSaveOrderWithItems() {
        var orderId = new UUID(1L, 1L);
        var userId = new UUID(2L, 2L);
        var productId = new UUID(3L, 3L);
        var order = Order.pending(userId, List.of(new OrderItem(
                productId, 2, new Money(new BigDecimal("25.50")))));
        when(orderRepository.save(any(OrderData.class))).thenAnswer(invocation -> {
            var data = invocation.getArgument(0, OrderData.class);
            return Mono.just(new OrderData(orderId, data.getUserId(), data.getStatus(), data.getCreatedAt()));
        });
        when(orderItemRepository.saveAll(any(Publisher.class))).thenReturn(Flux.just(new OrderItemData(
                new UUID(4L, 4L),
                orderId,
                productId,
                2,
                new BigDecimal("25.50"))));

        StepVerifier.create(adapter.save(order))
                .expectNextMatches(saved -> saved.getId().equals(orderId)
                        && saved.getUserId().equals(userId)
                        && saved.getItems().size() == 1
                        && saved.getItems().getFirst().getProductId().equals(productId))
                .verifyComplete();
    }

    @Test
    void shouldMapPersistenceFailuresWhenSavingOrder() {
        var order = Order.pending(new UUID(2L, 2L), List.of(new OrderItem(
                new UUID(3L, 3L), 2, new Money(new BigDecimal("25.50")))));
        when(orderRepository.save(any(OrderData.class)))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(adapter.save(order))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }

    @Test
    void shouldFindOrderByIdWithItems() {
        var orderId = new UUID(1L, 1L);
        var userId = new UUID(2L, 2L);
        var productId = new UUID(3L, 3L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(new OrderData(
                orderId,
                userId,
                "PENDING",
                java.time.Instant.parse("2026-08-17T12:00:00Z"))));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(Flux.just(new OrderItemData(
                new UUID(4L, 4L),
                orderId,
                productId,
                2,
                new BigDecimal("25.50"))));

        StepVerifier.create(adapter.findById(orderId))
                .expectNextMatches(order -> order.getId().equals(orderId)
                        && order.getUserId().equals(userId)
                        && order.getStatus().name().equals("PENDING")
                        && order.getItems().size() == 1
                        && order.getItems().getFirst().getProductId().equals(productId)
                        && order.getItems().getFirst().getQuantity().equals(2))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenOrderDoesNotExist() {
        var orderId = new UUID(1L, 1L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(orderId))
                .verifyComplete();
    }

    @Test
    void shouldMapPersistenceFailuresWhenFindingOrderById() {
        var orderId = new UUID(1L, 1L);
        when(orderRepository.findById(orderId))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(adapter.findById(orderId))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }
}
