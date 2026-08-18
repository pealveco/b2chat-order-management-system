package com.b2chat.ordermanagement.usecase.getuserorders;

import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetUserOrdersUseCase {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public Flux<Order> execute(UUID userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException(userId)))
                .flatMapMany(user -> orderRepository.findByUserId(userId));
    }
}
