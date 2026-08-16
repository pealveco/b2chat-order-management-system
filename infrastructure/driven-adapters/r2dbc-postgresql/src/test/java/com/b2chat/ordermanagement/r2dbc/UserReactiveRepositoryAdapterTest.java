package com.b2chat.ordermanagement.r2dbc;

import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.user.EmailAlreadyExistsException;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.r2dbc.user.UserData;
import com.b2chat.ordermanagement.r2dbc.user.UserReactiveRepository;
import com.b2chat.ordermanagement.r2dbc.user.UserReactiveRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReactiveRepositoryAdapterTest {
    @InjectMocks
    UserReactiveRepositoryAdapter repositoryAdapter;

    @Mock
    UserReactiveRepository repository;

    @Mock
    ObjectMapper mapper;

    @Test
    void shouldFindUserById() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Mono.just(new UserData(
                id,
                "juan@example.com",
                "Juan Perez",
                "Cra 10"
        )));

        Mono<User> result = repositoryAdapter.findById(id);

        StepVerifier.create(result)
                .expectNextMatches(user -> user.getId().equals(id)
                        && user.getEmail().getValue().equals("juan@example.com")
                        && user.getName().equals("Juan Perez")
                        && user.getAddress().equals("Cra 10"))
                .verifyComplete();
    }

    @Test
    void shouldMapPersistenceFailuresWhenFindingUserById() {
        var id = UUID.randomUUID();
        when(repository.findById(id))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(repositoryAdapter.findById(id))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }

    @Test
    void shouldSaveUser() {
        var id = UUID.randomUUID();
        var user = User.create(new Email("juan@example.com"), "Juan Perez", "Cra 10");
        when(repository.save(any(UserData.class))).thenReturn(Mono.just(new UserData(
                id,
                "juan@example.com",
                "Juan Perez",
                "Cra 10"
        )));

        StepVerifier.create(repositoryAdapter.save(user))
                .expectNextMatches(saved -> saved.getId().equals(id)
                        && saved.getEmail().getValue().equals("juan@example.com"))
                .verifyComplete();
    }

    @Test
    void shouldMapUniqueConstraintViolationToConflictError() {
        var user = User.create(new Email("juan@example.com"), "Juan Perez", "Cra 10");
        when(repository.save(any(UserData.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("duplicate email")));

        StepVerifier.create(repositoryAdapter.save(user))
                .expectError(EmailAlreadyExistsException.class)
                .verify();
    }

    @Test
    void shouldMapPersistenceFailuresWhenSavingUser() {
        var user = User.create(new Email("juan@example.com"), "Juan Perez", "Cra 10");
        when(repository.save(any(UserData.class)))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(repositoryAdapter.save(user))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }

    @Test
    void shouldCheckEmailExistence() {
        when(repository.existsByEmail("juan@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(repositoryAdapter.existsByEmail(new Email("juan@example.com")))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void shouldMapPersistenceFailuresWhenCheckingEmailExistence() {
        when(repository.existsByEmail("juan@example.com"))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection failed")));

        StepVerifier.create(repositoryAdapter.existsByEmail(new Email("juan@example.com")))
                .expectError(RepositoryUnavailableException.class)
                .verify();
    }
}
