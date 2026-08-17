package com.b2chat.ordermanagement.usecase.getorder;

import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.OrderNotFoundException;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

class GetOrderUseCaseTest {
    private OrderRepository orderRepository;
    private GetOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        useCase = new GetOrderUseCase(orderRepository);
    }

    @Test
    void shouldGetOrderById() {
        var orderId = new UUID(1L, 1L);
        var userId = new UUID(2L, 2L);
        var productId = new UUID(3L, 3L);
        var order = Order.pending(userId, List.of(new OrderItem(
                productId, 2, new Money(new BigDecimal("25.50"))))).withId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.execute(orderId))
                .expectNext(order)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenOrderDoesNotExist() {
        var orderId = new UUID(1L, 1L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(orderId))
                .expectError(OrderNotFoundException.class)
                .verify();
    }
}
