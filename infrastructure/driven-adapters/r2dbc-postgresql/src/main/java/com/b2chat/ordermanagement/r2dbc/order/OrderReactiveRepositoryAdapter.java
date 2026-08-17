package com.b2chat.ordermanagement.r2dbc.order;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.OrderStatus;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Repository
public class OrderReactiveRepositoryAdapter implements OrderRepository {
    private final OrderReactiveRepository orderRepository;
    private final OrderItemReactiveRepository orderItemRepository;

    public OrderReactiveRepositoryAdapter(OrderReactiveRepository orderRepository,
                                          OrderItemReactiveRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public Mono<Order> save(Order order) {
        return orderRepository.save(toOrderData(order))
                .flatMap(savedOrder -> saveItems(savedOrder.getId(), order.getItems())
                        .map(savedItems -> toOrder(savedOrder, savedItems)))
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Order repository is temporarily unavailable"));
    }

    private Mono<List<OrderItem>> saveItems(UUID orderId, List<OrderItem> items) {
        return Flux.fromIterable(items)
                .map(item -> toOrderItemData(orderId, item))
                .as(orderItemRepository::saveAll)
                .map(this::toOrderItem)
                .collectList();
    }

    private OrderData toOrderData(Order order) {
        return new OrderData(order.getId(), order.getUserId(), order.getStatus().name(), order.getCreatedAt());
    }

    private OrderItemData toOrderItemData(UUID orderId, OrderItem item) {
        return new OrderItemData(
                null,
                orderId,
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPriceAtOrderTime().getAmount());
    }

    private Order toOrder(OrderData data, List<OrderItem> items) {
        return new Order(
                data.getId(),
                data.getUserId(),
                items,
                OrderStatus.valueOf(data.getStatus()),
                data.getCreatedAt());
    }

    private OrderItem toOrderItem(OrderItemData data) {
        return new OrderItem(
                data.getProductId(),
                data.getQuantity(),
                new Money(data.getUnitPriceAtOrderTime()));
    }
}
