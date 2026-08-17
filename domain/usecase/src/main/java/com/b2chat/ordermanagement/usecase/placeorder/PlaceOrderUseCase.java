package com.b2chat.ordermanagement.usecase.placeorder;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.common.gateways.TransactionPort;
import com.b2chat.ordermanagement.model.order.EmptyOrderItemsException;
import com.b2chat.ordermanagement.model.order.InsufficientStockException;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.OrderPlacedEvent;
import com.b2chat.ordermanagement.model.order.gateways.OrderEventPublisher;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.orderitem.InvalidOrderItemQuantityException;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.ProductNotFoundException;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class PlaceOrderUseCase {
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductCachePort productCachePort;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionPort transactionPort;

    public Mono<Order> execute(UUID userId, List<PlaceOrderItemCommand> items) {
        var requestedItems = validateItems(items);
        var transaction = Mono.defer(() -> userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException(userId)))
                .thenMany(Flux.fromIterable(requestedItems))
                .concatMap(this::validateAndDiscountStock)
                .collectList()
                .map(orderItems -> Order.pending(userId, orderItems))
                .flatMap(orderRepository::save));

        return transactionPort.transactional(transaction)
                .flatMap(order -> evictProductsFromCache(requestedItems).thenReturn(order))
                .doOnNext(order -> orderEventPublisher.publishOrderPlaced(
                        new OrderPlacedEvent(order.getId(), order.getUserId(), order.getCreatedAt())));
    }

    private Mono<OrderItem> validateAndDiscountStock(PlaceOrderItemCommand item) {
        validateItem(item);
        return productRepository.findById(item.productId())
                .switchIfEmpty(Mono.error(new ProductNotFoundException(item.productId())))
                .flatMap(product -> discountStock(product, item.quantity()));
    }

    private Mono<OrderItem> discountStock(Product product, Integer quantity) {
        return productRepository.decrementStockIfAvailable(product.getId(), quantity)
                .flatMap(updated -> updated
                        ? Mono.just(new OrderItem(product.getId(), quantity, product.getPrice()))
                        : Mono.error(new InsufficientStockException(product.getId(), quantity)));
    }

    private Mono<Void> evictProductsFromCache(List<PlaceOrderItemCommand> items) {
        return Flux.fromIterable(items)
                .map(PlaceOrderItemCommand::productId)
                .distinct()
                .flatMap(productCachePort::evict)
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    private List<PlaceOrderItemCommand> validateItems(List<PlaceOrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderItemsException();
        }
        return List.copyOf(items);
    }

    private void validateItem(PlaceOrderItemCommand item) {
        if (item == null) {
            throw new EmptyOrderItemsException();
        }
        if (item.productId() == null) {
            throw new RequiredFieldException("productId");
        }
        if (item.quantity() == null || item.quantity() <= 0) {
            throw new InvalidOrderItemQuantityException();
        }
    }
}
