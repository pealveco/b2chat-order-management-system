package com.b2chat.ordermanagement.usecase.updateorderstatus;

import com.b2chat.ordermanagement.model.common.gateways.TransactionPort;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.InvalidOrderStatusTransitionException;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.OrderNotFoundException;
import com.b2chat.ordermanagement.model.order.OrderStatus;
import com.b2chat.ordermanagement.model.order.gateways.OrderEventPublisher;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
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

class UpdateOrderStatusUseCaseTest {
    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private OrderEventPublisher orderEventPublisher;
    private UpdateOrderStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        orderEventPublisher = Mockito.mock(OrderEventPublisher.class);
        TransactionPort transactionPort = new TransactionPort() {
            @Override
            public <T> Mono<T> transactional(Mono<T> publisher) {
                return publisher;
            }
        };
        useCase = new UpdateOrderStatusUseCase(orderRepository, productRepository, productCachePort,
                orderEventPublisher, transactionPort);
    }

    @Test
    void shouldUpdateStatusToProcessing() {
        var orderId = new UUID(1L, 1L);
        var order = order(orderId, OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(orderRepository.updateStatus(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.execute(orderId, OrderStatus.PROCESSING))
                .expectNextMatches(updated -> updated.getStatus() == OrderStatus.PROCESSING)
                .verifyComplete();

        verify(orderRepository).updateStatus(any(Order.class));
        verify(productRepository, never()).incrementStock(any(), Mockito.anyInt());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
    }

    @Test
    void shouldPublishCompletedEventWhenStatusBecomesCompleted() {
        var orderId = new UUID(1L, 1L);
        var order = order(orderId, OrderStatus.PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(orderRepository.updateStatus(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.execute(orderId, OrderStatus.COMPLETED))
                .expectNextMatches(updated -> updated.getStatus() == OrderStatus.COMPLETED)
                .verifyComplete();

        verify(orderEventPublisher).publishOrderCompleted(any());
    }

    @Test
    void shouldRestoreStockAndRefreshCacheWhenCancellingOrder() {
        var orderId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        var order = order(orderId, OrderStatus.PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(productRepository.incrementStock(productId, 2)).thenReturn(Mono.just(true));
        when(productRepository.findById(productId)).thenReturn(Mono.just(product(productId)));
        when(productCachePort.put(any())).thenReturn(Mono.empty());
        when(orderRepository.updateStatus(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.execute(orderId, OrderStatus.CANCELLED))
                .expectNextMatches(updated -> updated.getStatus() == OrderStatus.CANCELLED)
                .verifyComplete();

        verify(productRepository).incrementStock(productId, 2);
        verify(productCachePort).put(any());
        verify(orderEventPublisher, never()).publishOrderCompleted(any());
    }

    @Test
    void shouldFailWhenStockCannotBeRestored() {
        var orderId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        var order = order(orderId, OrderStatus.PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(productRepository.incrementStock(productId, 2)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(orderId, OrderStatus.CANCELLED))
                .expectError(RepositoryUnavailableException.class)
                .verify();

        verify(orderRepository, never()).updateStatus(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldReturnSameOrderWhenStatusIsUnchanged() {
        var orderId = new UUID(1L, 1L);
        var order = order(orderId, OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.execute(orderId, OrderStatus.PENDING))
                .expectNext(order)
                .verifyComplete();

        verify(orderRepository, never()).updateStatus(any());
        verify(productRepository, never()).incrementStock(any(), Mockito.anyInt());
    }

    @Test
    void shouldFailWhenOrderDoesNotExist() {
        var orderId = new UUID(1L, 1L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(orderId, OrderStatus.PROCESSING))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void shouldFailWhenTransitionIsInvalid() {
        var orderId = new UUID(1L, 1L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order(orderId, OrderStatus.COMPLETED)));

        StepVerifier.create(useCase.execute(orderId, OrderStatus.PENDING))
                .expectError(InvalidOrderStatusTransitionException.class)
                .verify();

        verify(orderRepository, never()).updateStatus(any());
    }

    private Order order(UUID orderId, OrderStatus status) {
        return new Order(orderId, new UUID(3L, 3L), List.of(new OrderItem(
                new UUID(2L, 2L), 2, new Money(new BigDecimal("25.50")))), status, null);
    }

    private Product product(UUID productId) {
        return new Product(productId, "Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), 10);
    }
}
