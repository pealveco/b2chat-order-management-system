package com.b2chat.ordermanagement.usecase.registeruser;

import com.b2chat.ordermanagement.model.email.InvalidEmailException;
import com.b2chat.ordermanagement.model.user.EmailAlreadyExistsException;
import com.b2chat.ordermanagement.model.user.User;
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

class RegisterUserUseCaseTest {
    private UserRepository userRepository;
    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        useCase = new RegisterUserUseCase(userRepository);
    }

    @Test
    void shouldRegisterUserWhenEmailDoesNotExist() {
        when(userRepository.existsByEmail(any())).thenReturn(Mono.just(false));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            var user = invocation.getArgument(0, User.class);
            return Mono.just(new User(new UUID(1L, 1L), user.getEmail(), user.getName(), user.getAddress()));
        });

        StepVerifier.create(useCase.execute("juan@example.com", "Juan Perez", "Cra 10"))
                .expectNextMatches(user -> user.getId() != null
                        && user.getEmail().getValue().equals("juan@example.com")
                        && user.getName().equals("Juan Perez")
                        && user.getAddress().equals("Cra 10"))
                .verifyComplete();
    }

    @Test
    void shouldFailWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail(any())).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("juan@example.com", "Juan Perez", "Cra 10"))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldFailWhenEmailIsInvalid() {
        StepVerifier.create(useCase.execute("invalid", "Juan Perez", "Cra 10"))
                .expectError(InvalidEmailException.class)
                .verify();

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }
}
