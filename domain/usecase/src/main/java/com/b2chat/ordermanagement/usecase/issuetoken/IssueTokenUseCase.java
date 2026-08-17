package com.b2chat.ordermanagement.usecase.issuetoken;

import com.b2chat.ordermanagement.model.auth.gateways.TokenIssuerPort;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class IssueTokenUseCase {
    private final UserRepository userRepository;
    private final TokenIssuerPort tokenIssuerPort;

    public Mono<String> execute(UUID userId, String email) {
        return findUser(userId, email)
                .flatMap(tokenIssuerPort::issue);
    }

    private Mono<User> findUser(UUID userId, String email) {
        if (userId != null) {
            return userRepository.findById(userId)
                    .switchIfEmpty(Mono.error(new UserNotFoundException(userId)));
        }

        var userEmail = new Email(email);
        return userRepository.findByEmail(userEmail)
                .switchIfEmpty(Mono.error(new UserNotFoundException(userEmail.getValue())));
    }
}
