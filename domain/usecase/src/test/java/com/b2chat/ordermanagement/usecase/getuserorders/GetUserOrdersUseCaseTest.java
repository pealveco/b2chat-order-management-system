package com.b2chat.ordermanagement.usecase.getuserorders;

import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
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

class GetUserOrdersUseCaseTest {
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private GetUserOrdersUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        useCase = new GetUserOrdersUseCase(userRepository, orderRepository);
    }

    @Test
    void shouldGetUserOrders() {
        var userId = new UUID(1L, 1L);
        var order = order(userId);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(orderRepository.findByUserId(userId)).thenReturn(Flux.just(order));

        StepVerifier.create(useCase.execute(userId))
                .expectNext(order)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenUserHasNoOrders() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(orderRepository.findByUserId(userId)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(userId))
                .verifyComplete();
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(orderRepository, never()).findByUserId(userId);
    }

    private User user(UUID id) {
        return new User(id, new Email("buyer@example.com"), "Buyer", "Address");
    }

    private Order order(UUID userId) {
        return Order.pending(userId, List.of(new OrderItem(
                new UUID(2L, 2L), 2, new Money(new BigDecimal("25.50"))))).withId(new UUID(3L, 3L));
    }
}
