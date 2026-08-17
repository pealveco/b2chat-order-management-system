package com.b2chat.ordermanagement.usecase.placeorder;

import com.b2chat.ordermanagement.model.common.gateways.TransactionPort;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.InsufficientStockException;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.gateways.OrderEventPublisher;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.orderitem.InvalidOrderItemQuantityException;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.ProductNotFoundException;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceOrderUseCaseTest {
    private UserRepository userRepository;
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private OrderRepository orderRepository;
    private OrderEventPublisher orderEventPublisher;
    private PlaceOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        orderEventPublisher = Mockito.mock(OrderEventPublisher.class);
        TransactionPort transactionPort = new TransactionPort() {
            @Override
            public <T> Mono<T> transactional(Mono<T> publisher) {
                return publisher;
            }
        };
        useCase = new PlaceOrderUseCase(userRepository, productRepository, productCachePort,
                orderRepository, orderEventPublisher, transactionPort);
    }

    @Test
    void shouldPlaceOrderAndPublishEvent() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        var orderId = new UUID(3L, 3L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.just(product(productId, 10)));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(Mono.just(true));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            var order = invocation.getArgument(0, Order.class);
            return Mono.just(order.withId(orderId));
        });
        when(productCachePort.put(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, List.of(new PlaceOrderItemCommand(productId, 2))))
                .expectNextMatches(order -> order.getId().equals(orderId)
                        && order.getUserId().equals(userId)
                        && order.getItems().size() == 1
                        && order.getItems().getFirst().getProductId().equals(productId))
                .verifyComplete();

        verify(orderRepository).save(any());
        verify(productCachePort).put(any());
        verify(orderEventPublisher).publishOrderPlaced(any());
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, List.of(new PlaceOrderItemCommand(new UUID(2L, 2L), 2))))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(productRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void shouldFailWhenProductDoesNotExist() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, List.of(new PlaceOrderItemCommand(productId, 2))))
                .expectError(ProductNotFoundException.class)
                .verify();

        verify(productRepository, never()).decrementStockIfAvailable(any(), Mockito.anyInt());
        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void shouldFailWhenStockIsInsufficient() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.just(product(productId, 1)));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(userId, List.of(new PlaceOrderItemCommand(productId, 2))))
                .expectError(InsufficientStockException.class)
                .verify();

        verify(orderRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void shouldFailWhenQuantityIsInvalidBeforeQueryingProduct() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));

        StepVerifier.create(useCase.execute(userId, List.of(new PlaceOrderItemCommand(productId, 0))))
                .expectError(InvalidOrderItemQuantityException.class)
                .verify();

        verify(productRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldSucceedEvenIfCacheUpdateFails() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        var orderId = new UUID(3L, 3L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.just(product(productId, 10)));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(Mono.just(true));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            var order = invocation.getArgument(0, Order.class);
            return Mono.just(order.withId(orderId));
        });
        when(productCachePort.put(any())).thenReturn(Mono.error(new RuntimeException("Cache unavailable")));

        StepVerifier.create(useCase.execute(userId, List.of(new PlaceOrderItemCommand(productId, 2))))
                .expectNextMatches(order -> order.getId().equals(orderId)
                        && order.getUserId().equals(userId))
                .verifyComplete();

        verify(orderRepository).save(any());
        verify(productCachePort).put(any());
        verify(orderEventPublisher).publishOrderPlaced(any());
    }

    @Test
    void shouldFailWhenItemsListIsNull() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));

        org.junit.jupiter.api.Assertions.assertThrows(com.b2chat.ordermanagement.model.order.EmptyOrderItemsException.class,
                () -> useCase.execute(userId, null).block());

        verify(userRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldFailWhenItemsListIsEmpty() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));

        org.junit.jupiter.api.Assertions.assertThrows(com.b2chat.ordermanagement.model.order.EmptyOrderItemsException.class,
                () -> useCase.execute(userId, List.of()).block());

        verify(userRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }


    @Test
    void shouldFailWhenProductIdIsNull() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));

        org.junit.jupiter.api.Assertions.assertThrows(com.b2chat.ordermanagement.model.common.RequiredFieldException.class,
                () -> useCase.execute(userId, List.of(new PlaceOrderItemCommand(null, 2))).block());

        verify(productRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    private User user(UUID id) {
        return new User(id, new Email("buyer@example.com"), "Buyer", "Address");
    }

    private Product product(UUID id, int stock) {
        return new Product(id, "Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), stock);
    }
}
