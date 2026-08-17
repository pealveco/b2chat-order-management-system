package com.b2chat.ordermanagement.api.config;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.api.auth.JwtTokenIssuerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

class AuthorizationJwtTest {
    private static final String ISSUER = "http://localhost/auth";
    private static final String CLIENT_ID = "order-management-client";
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private WebTestClient webTestClient;
    private String validToken;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper();
        var authorizationJwt = new AuthorizationJwt(ISSUER, CLIENT_ID, "/roles", SECRET, objectMapper);
        var security = new WebFilterChainProxy(List.of(authorizationJwt.filterChain(ServerHttpSecurity.http())));
        var router = route(GET("/products"), request -> ServerResponse.ok().build())
                .andRoute(POST("/auth/token"), request -> ServerResponse.ok().build())
                .andRoute(POST("/products"), request -> ServerResponse.ok().build());
        var handlerStrategies = HandlerStrategies.builder()
                .exceptionHandler(new GlobalErrorWebExceptionHandler(objectMapper))
                .build();

        webTestClient = WebTestClient.bindToRouterFunction(router)
                .webFilter(security)
                .handlerStrategies(handlerStrategies)
                .build();

        var tokenIssuer = new JwtTokenIssuerAdapter(authorizationJwt.jwtEncoder(), ISSUER, CLIENT_ID, 60);
        validToken = tokenIssuer.issue(new User(UUID.randomUUID(), new Email("demo@example.com"), "Demo", "Address"))
                .block();
    }

    @Test
    void shouldAllowGetWithoutToken() {
        webTestClient.get()
                .uri("/products")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAuthTokenEndpointWithoutToken() {
        webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectWriteEndpointWithoutToken() {
        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.error.status").isEqualTo(401);
    }

    @Test
    void shouldRejectWriteEndpointWithInvalidToken() {
        webTestClient.post()
                .uri("/products")
                .header("Authorization", "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void shouldAllowWriteEndpointWithValidToken() {
        webTestClient.post()
                .uri("/products")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }
}
