package com.b2chat.ordermanagement.r2dbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ProductStockConcurrencyIntegrationTest {
    private static final UUID PRODUCT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "b2chat_orders")
            .withEnv("POSTGRES_USER", "b2chat")
            .withEnv("POSTGRES_PASSWORD", "b2chat_dev_password");

    @BeforeEach
    void setUp() throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS products");
            statement.execute("""
                    CREATE TABLE products (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        description TEXT,
                        price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
                        stock INTEGER NOT NULL CHECK (stock >= 0),
                        active BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);
        }
    }

    @Test
    void shouldAllowExactlyOneOrderWhenTwoRequestsCompeteForLastItem() throws Exception {
        insertProductWithStock(1);

        var results = runConcurrentDiscounts(2);

        assertEquals(1, successCount(results));
        assertEquals(1, failureCount(results));
        assertEquals(0, currentStock());
    }

    @Test
    void shouldNeverLeaveNegativeStockUnderMultipleConcurrentRequests() throws Exception {
        insertProductWithStock(3);

        var results = runConcurrentDiscounts(10);

        assertEquals(3, successCount(results));
        assertEquals(7, failureCount(results));
        assertEquals(0, currentStock());
        assertTrue(currentStock() >= 0);
    }

    private List<Boolean> runConcurrentDiscounts(int attempts) throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts);
        try {
            var tasks = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(ignored -> (Callable<Boolean>) () -> {
                        start.await();
                        return decrementStockIfAvailable(PRODUCT_ID, 1);
                    })
                    .toList();
            var futures = tasks.stream()
                    .map(executor::submit)
                    .toList();

            start.countDown();

            return futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    })
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean decrementStockIfAvailable(UUID productId, int quantity) throws SQLException {
        try (var connection = connection();
             var statement = connection.prepareStatement("""
                     UPDATE products
                     SET stock = stock - ?
                     WHERE id = ?
                       AND active = TRUE
                       AND stock >= ?
                     """)) {
            statement.setInt(1, quantity);
            statement.setObject(2, productId);
            statement.setInt(3, quantity);
            return statement.executeUpdate() > 0;
        }
    }

    private void insertProductWithStock(int stock) throws SQLException {
        try (var connection = connection();
             var statement = connection.prepareStatement("""
                     INSERT INTO products (id, name, description, price, stock, active)
                     VALUES (?, 'Concurrency Product', 'Race condition test product', ?, ?, TRUE)
                     """)) {
            statement.setObject(1, PRODUCT_ID);
            statement.setBigDecimal(2, new BigDecimal("10.00"));
            statement.setInt(3, stock);
            statement.executeUpdate();
        }
    }

    private int currentStock() throws SQLException {
        try (var connection = connection();
             var statement = connection.prepareStatement("SELECT stock FROM products WHERE id = ?")) {
            statement.setObject(1, PRODUCT_ID);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt("stock");
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/b2chat_orders",
                "b2chat",
                "b2chat_dev_password");
    }

    private long successCount(List<Boolean> results) {
        return results.stream().filter(Boolean::booleanValue).count();
    }

    private long failureCount(List<Boolean> results) {
        return results.stream().filter(result -> !result).count();
    }
}
