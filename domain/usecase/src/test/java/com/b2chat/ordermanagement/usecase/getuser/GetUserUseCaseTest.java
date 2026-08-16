package com.b2chat.ordermanagement.usecase.getuser;

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

import static org.mockito.Mockito.when;

class GetUserUseCaseTest {
    private UserRepository userRepository;
    private GetUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        useCase = new GetUserUseCase(userRepository);
    }

    @Test
    void shouldGetUserById() {
        var id = new UUID(1L, 1L);
        var user = new User(id, new Email("juan@example.com"), "Juan Perez", "Cra 10");
        when(userRepository.findById(id)).thenReturn(Mono.just(user));

        StepVerifier.create(useCase.execute(id))
                .expectNext(user)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        var id = new UUID(1L, 1L);
        when(userRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .expectError(UserNotFoundException.class)
                .verify();
    }
}
