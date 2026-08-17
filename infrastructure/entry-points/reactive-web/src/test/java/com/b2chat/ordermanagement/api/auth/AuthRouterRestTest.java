package com.b2chat.ordermanagement.api.auth;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.model.auth.gateways.TokenIssuerPort;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import com.b2chat.ordermanagement.usecase.issuetoken.IssueTokenUseCase;
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

class AuthRouterRestTest {
    private UserRepository userRepository;
    private TokenIssuerPort tokenIssuerPort;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        tokenIssuerPort = Mockito.mock(TokenIssuerPort.class);
        var issueTokenUseCase = new IssueTokenUseCase(userRepository, tokenIssuerPort);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var handler = new AuthHandler(issueTokenUseCase, new RequestValidator(validator), 60);
        var router = new AuthRouterRest().authRoutes(handler);
        var handlerStrategies = HandlerStrategies.builder()
                .exceptionHandler(new GlobalErrorWebExceptionHandler(new ObjectMapper()))
                .build();
        webTestClient = WebTestClient.bindToRouterFunction(router)
                .handlerStrategies(handlerStrategies)
                .build();
    }

    @Test
    void shouldIssueTokenByUserId() {
        var userId = UUID.randomUUID();
        var user = new User(userId, new Email("demo@example.com"), "Demo", "Address");
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(tokenIssuerPort.issue(user)).thenReturn(Mono.just("signed-token"));

        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"%s"}
                        """.formatted(userId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.token").isEqualTo("signed-token")
                .jsonPath("$.data.tokenType").isEqualTo("Bearer")
                .jsonPath("$.data.expiresInSeconds").isEqualTo(3600)
                .jsonPath("$.meta.path").isEqualTo("/auth/token")
                .jsonPath("$.meta.timestamp").exists();
    }

    @Test
    void shouldIssueTokenByEmail() {
        var user = new User(UUID.randomUUID(), new Email("demo@example.com"), "Demo", "Address");
        when(userRepository.findByEmail(new Email("demo@example.com"))).thenReturn(Mono.just(user));
        when(tokenIssuerPort.issue(user)).thenReturn(Mono.just("signed-token"));

        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"demo@example.com"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.token").isEqualTo("signed-token");
    }

    @Test
    void shouldReturnBadRequestWhenIdentifierIsMissing() {
        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.details[0].field").isEqualTo("userId|email");

        verify(tokenIssuerPort, never()).issue(any());
    }

    @Test
    void shouldReturnBadRequestWhenBothIdentifiersArePresent() {
        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"00000000-0000-0001-0000-000000000001","email":"demo@example.com"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request must include exactly one identifier: userId or email");

        verify(tokenIssuerPort, never()).issue(any());
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsInvalid() {
        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"invalid-email"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.details[?(@.field == 'email')].message")
                .isEqualTo("email must have a valid format");

        verify(tokenIssuerPort, never()).issue(any());
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"%s"}
                        """.formatted(userId))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("USER_NOT_FOUND");

        verify(tokenIssuerPort, never()).issue(any());
    }

    @Test
    void shouldReturnUnsupportedMediaTypeWhenContentTypeIsNotJson() {
        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("invalid")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}
