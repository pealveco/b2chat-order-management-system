package com.b2chat.ordermanagement.integration;

import com.b2chat.ordermanagement.MainApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Boots the full application context (WebFlux entry-points, use cases, R2DBC and Redis adapters,
 * JWT security) against real Postgres and Redis instances provided by Testcontainers, so subclasses
 * exercise the actual integration between layers instead of mocked gateways.
 */
@SpringBootTest(classes = MainApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
abstract class IntegrationTestBase {
    private static final String POSTGRES_DB = "b2chat_orders";
    private static final String POSTGRES_USER = "b2chat";
    private static final String POSTGRES_PASSWORD = "b2chat_dev_password";
    private static final String JWT_SECRET = "integration-test-jwt-secret-minimum-32-bytes-long";

    // Singleton container pattern: started once and shared by every subclass across the whole test
    // run (never stopped explicitly, Testcontainers' Ryuk reaper cleans them up at JVM exit). Using
    // JUnit's @Testcontainers/@Container class-lifecycle management here would stop these containers
    // after the first test class finishes, breaking every subclass that runs afterwards.
    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", POSTGRES_DB)
            .withEnv("POSTGRES_USER", POSTGRES_USER)
            .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
            // The official Postgres image opens its port, restarts internally after initdb, then
            // opens it again. Waiting for the "ready to accept connections" message (twice: once for
            // the transient init instance, once for the real one) avoids connecting during that gap.
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofSeconds(60)));

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + POSTGRES_DB);
        registry.add("spring.r2dbc.username", () -> POSTGRES_USER);
        registry.add("spring.r2dbc.password", () -> POSTGRES_PASSWORD);

        registry.add("adapters.r2dbc.host", POSTGRES::getHost);
        registry.add("adapters.r2dbc.port", () -> POSTGRES.getMappedPort(5432));
        registry.add("adapters.r2dbc.database", () -> POSTGRES_DB);
        registry.add("adapters.r2dbc.schema", () -> "public");
        registry.add("adapters.r2dbc.username", () -> POSTGRES_USER);
        registry.add("adapters.r2dbc.password", () -> POSTGRES_PASSWORD);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    protected WebTestClient webTestClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected UUID registerUser(String email, String name, String address) {
        var body = webTestClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"%s","name":"%s","address":"%s"}
                        """.formatted(email, name, address))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        return UUID.fromString(readTree(body).at("/data/id").asString());
    }

    protected String issueTokenFor(UUID userId) {
        var body = webTestClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"%s"}
                        """.formatted(userId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        return readTree(body).at("/data/token").asString();
    }

    protected UUID createProduct(String token, String name, BigDecimal price, int stock) {
        var body = webTestClient.post()
                .uri("/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"%s","description":"Integration test product","price":%s,"stock":%d}
                        """.formatted(name, price, stock))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        return UUID.fromString(readTree(body).at("/data/id").asString());
    }

    protected UUID extractId(byte[] responseBody) {
        return UUID.fromString(readTree(responseBody).at("/data/id").asString());
    }

    private JsonNode readTree(byte[] body) {
        return objectMapper.readTree(body);
    }
}
