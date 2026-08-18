package com.b2chat.ordermanagement.usecase.registeruser;

import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.EmailAlreadyExistsException;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RegisterUserUseCase {
    private final UserRepository userRepository;

    public Mono<User> execute(String email, String name, String address) {
        return Mono.defer(() -> {
            var userEmail = new Email(email);
            var user = User.create(userEmail, name, address);

            return userRepository.existsByEmail(userEmail)
                    .flatMap(exists -> exists
                            ? Mono.error(new EmailAlreadyExistsException(userEmail.getValue()))
                            : userRepository.save(user));
        });
    }
}
