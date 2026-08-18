package com.b2chat.ordermanagement.usecase.issuetoken;

import com.b2chat.ordermanagement.model.auth.gateways.TokenIssuerPort;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.UserNotFoundException;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssueTokenUseCaseTest {
    private UserRepository userRepository;
    private TokenIssuerPort tokenIssuerPort;
    private IssueTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        tokenIssuerPort = Mockito.mock(TokenIssuerPort.class);
        useCase = new IssueTokenUseCase(userRepository, tokenIssuerPort);
    }

    @Test
    void shouldIssueTokenByUserId() {
        var userId = UUID.randomUUID();
        var user = new User(userId, new Email("demo@example.com"), "Demo", "Address");
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(tokenIssuerPort.issue(user)).thenReturn(Mono.just("signed-token"));

        StepVerifier.create(useCase.execute(userId, null))
                .expectNext("signed-token")
                .verifyComplete();
    }

    @Test
    void shouldIssueTokenByEmail() {
        var user = new User(UUID.randomUUID(), new Email("demo@example.com"), "Demo", "Address");
        when(userRepository.findByEmail(new Email("demo@example.com"))).thenReturn(Mono.just(user));
        when(tokenIssuerPort.issue(user)).thenReturn(Mono.just("signed-token"));

        StepVerifier.create(useCase.execute(null, "demo@example.com"))
                .expectNext("signed-token")
                .verifyComplete();
    }

    @Test
    void shouldFailWhenUserIdDoesNotExist() {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(userId, null))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(tokenIssuerPort, never()).issue(any());
    }

    @Test
    void shouldFailWhenEmailDoesNotExist() {
        when(userRepository.findByEmail(new Email("demo@example.com"))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(null, "demo@example.com"))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(tokenIssuerPort, never()).issue(any());
    }
}
