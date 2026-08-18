package com.b2chat.ordermanagement.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

class OrderIntegrationTest extends IntegrationTestBase {

    @Test
    void shouldPlaceOrderAndPersistItAcrossPostgresAndRedis() {
        var userId = registerUser(uniqueEmail(), "Buyer", "Cra 10");
        var token = issueTokenFor(userId);
        var productId = createProduct(token, "Integration Keyboard", new BigDecimal("25.50"), 10);

        var orderId = placeOrder(token, userId, productId, 2);

        // Round-trips through a fresh GET to prove the order was actually persisted in Postgres,
        // not just echoed back from the create response.
        webTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(orderId.toString())
                .jsonPath("$.data.userId").isEqualTo(userId.toString())
                .jsonPath("$.data.status").isEqualTo("PENDING")
                .jsonPath("$.data.items[0].productId").isEqualTo(productId.toString())
                .jsonPath("$.data.items[0].quantity").isEqualTo(2)
                .jsonPath("$.data.items[0].unitPriceAtOrderTime").isEqualTo(25.50);
    }

    @Test
    void shouldReturnConflictWhenPlacingOrderWithInsufficientStock() {
        var userId = registerUser(uniqueEmail(), "Buyer", "Cra 10");
        var token = issueTokenFor(userId);
        var productId = createProduct(token, "Scarce Product", new BigDecimal("10.00"), 1);

        webTestClient.post()
                .uri("/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"%s","items":[{"productId":"%s","quantity":2}]}
                        """.formatted(userId, productId))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INSUFFICIENT_STOCK")
                .jsonPath("$.error.status").isEqualTo(409);

        // Stock must remain untouched since the discount is atomic in Postgres.
        webTestClient.get()
                .uri("/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.id == '" + productId + "')].stock").isEqualTo(1);
    }

    @Test
    void shouldUpdateOrderStatusAndPersistTheChange() {
        var userId = registerUser(uniqueEmail(), "Buyer", "Cra 10");
        var token = issueTokenFor(userId);
        var productId = createProduct(token, "Status Update Product", new BigDecimal("15.00"), 5);
        var orderId = placeOrder(token, userId, productId, 1);

        webTestClient.put()
                .uri("/orders/{id}/status", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"PROCESSING"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(orderId.toString())
                .jsonPath("$.data.status").isEqualTo("PROCESSING");

        // Round-trips through a fresh GET to prove the status change was actually persisted.
        webTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("PROCESSING");
    }

    private UUID placeOrder(String token, UUID userId, UUID productId, int quantity) {
        var body = webTestClient.post()
                .uri("/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"%s","items":[{"productId":"%s","quantity":%d}]}
                        """.formatted(userId, productId, quantity))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.id").exists()
                .returnResult()
                .getResponseBody();

        return extractId(body);
    }
}
