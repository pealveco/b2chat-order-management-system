package com.b2chat.ordermanagement.usecase.getuser;

import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetUserUseCase {
    private final UserRepository userRepository;

    public Mono<User> execute(UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    }
}
