package com.b2chat.ordermanagement.api.user;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import com.b2chat.ordermanagement.usecase.getuser.GetUserUseCase;
import com.b2chat.ordermanagement.usecase.getuserorders.GetUserOrdersUseCase;
import com.b2chat.ordermanagement.usecase.registeruser.RegisterUserUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRouterRestTest {
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        var registerUserUseCase = new RegisterUserUseCase(userRepository);
        var getUserUseCase = new GetUserUseCase(userRepository);
        var getUserOrdersUseCase = new GetUserOrdersUseCase(userRepository, orderRepository);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var handler = new UserHandler(registerUserUseCase, getUserUseCase, getUserOrdersUseCase,
                new RequestValidator(validator));
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
    void shouldGetUserOrders() {
        var userId = new UUID(1L, 1L);
        var orderId = new UUID(3L, 3L);
        var productId = new UUID(2L, 2L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(new User(
                userId,
                new Email("ana@example.com"),
                "Ana Demo",
                "Calle 123"
        )));
        when(orderRepository.findByUserId(userId)).thenReturn(Flux.just(Order.pending(userId, List.of(new OrderItem(
                productId, 2, new Money(new BigDecimal("25.50"))))).withId(orderId)));

        webTestClient.get()
                .uri("/users/{id}/orders", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo(orderId.toString())
                .jsonPath("$.data[0].userId").isEqualTo(userId.toString())
                .jsonPath("$.data[0].status").isEqualTo("PENDING")
                .jsonPath("$.data[0].items[0].productId").isEqualTo(productId.toString())
                .jsonPath("$.data[0].items[0].quantity").isEqualTo(2)
                .jsonPath("$.data[0].items[0].unitPriceAtOrderTime").isEqualTo(25.50)
                .jsonPath("$.meta.path").isEqualTo("/users/" + userId + "/orders");
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoOrders() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(new User(
                userId,
                new Email("ana@example.com"),
                "Ana Demo",
                "Calle 123"
        )));
        when(orderRepository.findByUserId(userId)).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/users/{id}/orders", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(0)
                .jsonPath("$.meta.path").isEqualTo("/users/" + userId + "/orders");
    }

    @Test
    void shouldReturnNotFoundWhenGettingOrdersForMissingUser() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/users/{id}/orders", userId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("USER_NOT_FOUND")
                .jsonPath("$.error.status").isEqualTo(404)
                .jsonPath("$.error.path").isEqualTo("/users/" + userId + "/orders");

        verify(orderRepository, never()).findByUserId(userId);
    }

    @Test
    void shouldReturnBadRequestWhenGettingOrdersWithInvalidUserId() {
        webTestClient.get()
                .uri("/users/not-a-uuid/orders")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Path variable id must be a valid UUID")
                .jsonPath("$.error.status").isEqualTo(400)
                .jsonPath("$.error.path").isEqualTo("/users/not-a-uuid/orders");
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
