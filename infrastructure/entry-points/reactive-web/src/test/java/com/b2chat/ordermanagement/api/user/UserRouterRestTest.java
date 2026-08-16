package com.b2chat.ordermanagement.api.user;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import com.b2chat.ordermanagement.usecase.getuser.GetUserUseCase;
import com.b2chat.ordermanagement.usecase.registeruser.RegisterUserUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRouterRestTest {
    private UserRepository userRepository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        var registerUserUseCase = new RegisterUserUseCase(userRepository);
        var getUserUseCase = new GetUserUseCase(userRepository);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var handler = new UserHandler(registerUserUseCase, getUserUseCase, new RequestValidator(validator));
        var router = new UserRouterRest().userRoutes(handler);
        var handlerStrategies = HandlerStrategies.builder()
                .exceptionHandler(new GlobalErrorWebExceptionHandler(new ObjectMapper()))
                .build();
        webTestClient = WebTestClient.bindToRouterFunction(router)
                .handlerStrategies(handlerStrategies)
                .build();
    }

    @Test
    void shouldRegisterUser() {
        when(userRepository.existsByEmail(any())).thenReturn(Mono.just(false));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            var user = invocation.getArgument(0, User.class);
            return Mono.just(new User(new UUID(1L, 1L), user.getEmail(), user.getName(), user.getAddress()));
        });

        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"juan@example.com","name":"Juan Perez","address":"Cra 10"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.data.id").exists()
                .jsonPath("$.data.email").isEqualTo("juan@example.com")
                .jsonPath("$.data.name").isEqualTo("Juan Perez")
                .jsonPath("$.data.address").isEqualTo("Cra 10")
                .jsonPath("$.meta.path").isEqualTo("/users")
                .jsonPath("$.meta.timestamp").exists();
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail(any())).thenReturn(Mono.just(true));

        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"juan@example.com","name":"Juan Perez","address":"Cra 10"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("EMAIL_ALREADY_EXISTS")
                .jsonPath("$.error.message").isEqualTo("A user with email juan@example.com already exists")
                .jsonPath("$.error.status").isEqualTo(409)
                .jsonPath("$.error.path").isEqualTo("/users")
                .jsonPath("$.error.timestamp").exists();

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsInvalid() {
        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"invalid","name":"Juan Perez","address":"Cra 10"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request validation failed")
                .jsonPath("$.error.status").isEqualTo(400)
                .jsonPath("$.error.path").isEqualTo("/users")
                .jsonPath("$.error.details[?(@.field == 'email')].message").isEqualTo("email format is invalid");
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsAreBlank() {
        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":" ","name":" ","address":""}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request validation failed")
                .jsonPath("$.error.status").isEqualTo(400)
                .jsonPath("$.error.details[?(@.field == 'email')]").exists()
                .jsonPath("$.error.details[?(@.field == 'name')]").exists()
                .jsonPath("$.error.details[?(@.field == 'address')]").exists();
    }

    @Test
    void shouldReturnBadRequestWhenBodyIsEmpty() {
        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("EMPTY_BODY")
                .jsonPath("$.error.message").isEqualTo("Request body is required")
                .jsonPath("$.error.status").isEqualTo(400);
    }

    @Test
    void shouldReturnBadRequestWhenBodyIsMalformed() {
        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request body is invalid")
                .jsonPath("$.error.status").isEqualTo(400);
    }

    @Test
    void shouldReturnUnsupportedMediaTypeWhenContentTypeIsNotJson() {
        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("email=juan@example.com")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE")
                .jsonPath("$.error.message").isEqualTo("Content-Type must be application/json")
                .jsonPath("$.error.status").isEqualTo(415);
    }

    @Test
    void shouldReturnServiceUnavailableWhenPersistenceFails() {
        when(userRepository.existsByEmail(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException(
                        "Persistence repository is temporarily unavailable")));

        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"juan@example.com","name":"Juan Perez","address":"Cra 10"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.error.message").isEqualTo("Persistence repository is temporarily unavailable")
                .jsonPath("$.error.status").isEqualTo(503);
    }

    @Test
    void shouldGetUserById() {
        var id = new UUID(1L, 1L);
        when(userRepository.findById(id)).thenReturn(Mono.just(new User(
                id,
                new Email("ana@example.com"),
                "Ana Demo",
                "Calle 123"
        )));

        webTestClient.get()
                .uri("/users/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(id.toString())
                .jsonPath("$.data.email").isEqualTo("ana@example.com")
                .jsonPath("$.data.name").isEqualTo("Ana Demo")
                .jsonPath("$.data.address").isEqualTo("Calle 123")
                .jsonPath("$.meta.path").isEqualTo("/users/" + id)
                .jsonPath("$.meta.timestamp").exists();
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() {
        var id = new UUID(1L, 1L);
        when(userRepository.findById(id)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/users/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("USER_NOT_FOUND")
                .jsonPath("$.error.message").isEqualTo("User with id " + id + " was not found")
                .jsonPath("$.error.status").isEqualTo(404)
                .jsonPath("$.error.path").isEqualTo("/users/" + id);
    }

    @Test
    void shouldReturnBadRequestWhenUserIdIsNotUuid() {
        webTestClient.get()
                .uri("/users/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Path variable id must be a valid UUID")
                .jsonPath("$.error.status").isEqualTo(400)
                .jsonPath("$.error.path").isEqualTo("/users/not-a-uuid");
    }
}
