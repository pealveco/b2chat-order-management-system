package com.b2chat.ordermanagement.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class UserIntegrationTest extends IntegrationTestBase {

    @Test
    void shouldRegisterUserAndPersistItInPostgres() {
        var email = uniqueEmail();

        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"%s","name":"Juan Perez","address":"Cra 10"}
                        """.formatted(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.id").exists()
                .jsonPath("$.data.email").isEqualTo(email)
                .jsonPath("$.data.name").isEqualTo("Juan Perez")
                .jsonPath("$.data.address").isEqualTo("Cra 10");
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() {
        var email = uniqueEmail();
        registerUser(email, "Original User", "Cra 10");

        webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"%s","name":"Second User","address":"Cra 20"}
                        """.formatted(email))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("EMAIL_ALREADY_EXISTS")
                .jsonPath("$.error.message").isEqualTo("A user with email " + email + " already exists")
                .jsonPath("$.error.status").isEqualTo(409);
    }
}
