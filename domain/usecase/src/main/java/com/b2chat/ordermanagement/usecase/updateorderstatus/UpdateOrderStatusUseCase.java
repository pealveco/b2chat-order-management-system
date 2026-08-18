package com.b2chat.ordermanagement.usecase.updateorderstatus;

import com.b2chat.ordermanagement.model.common.gateways.TransactionPort;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.order.InvalidOrderStatusTransitionException;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.OrderCompletedEvent;
import com.b2chat.ordermanagement.model.order.OrderNotFoundException;
import com.b2chat.ordermanagement.model.order.OrderStatus;
import com.b2chat.ordermanagement.model.order.gateways.OrderEventPublisher;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class UpdateOrderStatusUseCase {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductCachePort productCachePort;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionPort transactionPort;

    public Mono<Order> execute(UUID id, OrderStatus targetStatus) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(id)))
                .flatMap(order -> updateStatus(order, targetStatus));
    }

    private Mono<Order> updateStatus(Order order, OrderStatus targetStatus) {
        if (!order.getStatus().canTransitionTo(targetStatus)) {
            return Mono.error(new InvalidOrderStatusTransitionException(order.getStatus(), targetStatus));
        }
        if (order.getStatus() == targetStatus) {
            return Mono.just(order);
        }

        var updatedOrder = order.withStatus(targetStatus);
        var transaction = restoreStockIfCancelled(order, targetStatus)
                .then(Mono.defer(() -> orderRepository.updateStatus(updatedOrder)));

        return transactionPort.transactional(transaction)
                .flatMap(savedOrder -> refreshProductsInCacheIfCancelled(order, targetStatus).thenReturn(savedOrder))
                .doOnNext(savedOrder -> publishCompletedEvent(savedOrder, targetStatus));
    }

    private Mono<Void> restoreStockIfCancelled(Order order, OrderStatus targetStatus) {
        if (targetStatus != OrderStatus.CANCELLED) {
            return Mono.empty();
        }
        return Flux.fromIterable(order.getItems())
                .concatMap(item -> productRepository.incrementStock(item.getProductId(), item.getQuantity())
                        .flatMap(updated -> updated
                                ? Mono.empty()
                                : Mono.error(new RepositoryUnavailableException(
                                        "Product repository is temporarily unavailable"))))
                .then();
    }

    private Mono<Void> refreshProductsInCacheIfCancelled(Order order, OrderStatus targetStatus) {
        if (targetStatus != OrderStatus.CANCELLED) {
            return Mono.empty();
        }
        return Flux.fromIterable(order.getItems())
                .map(item -> item.getProductId())
                .distinct()
                .flatMap(this::refreshProductInCache)
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    private Mono<Void> refreshProductInCache(UUID productId) {
        return productRepository.findById(productId)
                .flatMap(productCachePort::put)
                .then();
    }

    private void publishCompletedEvent(Order order, OrderStatus targetStatus) {
        if (targetStatus == OrderStatus.COMPLETED) {
            orderEventPublisher.publishOrderCompleted(
                    new OrderCompletedEvent(order.getId(), order.getUserId(), Instant.now()));
        }
    }
}
