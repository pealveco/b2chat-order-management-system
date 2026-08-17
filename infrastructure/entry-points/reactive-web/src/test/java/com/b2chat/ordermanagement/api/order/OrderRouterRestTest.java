package com.b2chat.ordermanagement.api.order;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.model.common.gateways.TransactionPort;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.order.Order;
import com.b2chat.ordermanagement.model.orderitem.OrderItem;
import com.b2chat.ordermanagement.model.order.gateways.OrderEventPublisher;
import com.b2chat.ordermanagement.model.order.gateways.OrderRepository;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import com.b2chat.ordermanagement.usecase.getorder.GetOrderUseCase;
import com.b2chat.ordermanagement.usecase.placeorder.PlaceOrderUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderRouterRestTest {
    private UserRepository userRepository;
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private OrderRepository orderRepository;
    private OrderEventPublisher orderEventPublisher;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        orderEventPublisher = Mockito.mock(OrderEventPublisher.class);
        TransactionPort transactionPort = new TransactionPort() {
            @Override
            public <T> Mono<T> transactional(Mono<T> publisher) {
                return publisher;
            }
        };
        var placeOrderUseCase = new PlaceOrderUseCase(userRepository, productRepository, productCachePort,
                orderRepository, orderEventPublisher, transactionPort);
        var getOrderUseCase = new GetOrderUseCase(orderRepository);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var handler = new OrderHandler(placeOrderUseCase, getOrderUseCase, new RequestValidator(validator));
        var router = new OrderRouterRest().orderRoutes(handler);
        var handlerStrategies = HandlerStrategies.builder()
                .exceptionHandler(new GlobalErrorWebExceptionHandler(new ObjectMapper()))
                .build();
        webTestClient = WebTestClient.bindToRouterFunction(router)
                .handlerStrategies(handlerStrategies)
                .build();
    }

    @Test
    void shouldPlaceOrder() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        var orderId = new UUID(3L, 3L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.just(product(productId)));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(Mono.just(true));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            var order = invocation.getArgument(0, Order.class);
            return Mono.just(order.withId(orderId));
        });
        when(productCachePort.evict(productId)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"00000000-0000-0001-0000-000000000001",
                         "items":[{"productId":"00000000-0000-0002-0000-000000000002","quantity":2}]}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(orderId.toString())
                .jsonPath("$.data.userId").isEqualTo(userId.toString())
                .jsonPath("$.data.status").isEqualTo("PENDING")
                .jsonPath("$.data.items[0].productId").isEqualTo(productId.toString())
                .jsonPath("$.data.items[0].quantity").isEqualTo(2)
                .jsonPath("$.meta.path").isEqualTo("/orders");

        verify(orderEventPublisher).publishOrderPlaced(any());
    }

    @Test
    void shouldGetOrderById() {
        var orderId = new UUID(3L, 3L);
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(Order.pending(userId, List.of(new OrderItem(
                productId, 2, new Money(new BigDecimal("25.50"))))).withId(orderId)));

        webTestClient.get()
                .uri("/orders/00000000-0000-0003-0000-000000000003")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(orderId.toString())
                .jsonPath("$.data.userId").isEqualTo(userId.toString())
                .jsonPath("$.data.status").isEqualTo("PENDING")
                .jsonPath("$.data.items[0].productId").isEqualTo(productId.toString())
                .jsonPath("$.data.items[0].quantity").isEqualTo(2)
                .jsonPath("$.data.items[0].unitPriceAtOrderTime").isEqualTo(25.50)
                .jsonPath("$.meta.path").isEqualTo("/orders/00000000-0000-0003-0000-000000000003");
    }

    @Test
    void shouldReturnNotFoundWhenOrderDoesNotExist() {
        var orderId = new UUID(3L, 3L);
        when(orderRepository.findById(orderId)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/orders/00000000-0000-0003-0000-000000000003")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("ORDER_NOT_FOUND")
                .jsonPath("$.error.status").isEqualTo(404);
    }

    @Test
    void shouldReturnBadRequestWhenOrderIdIsInvalid() {
        webTestClient.get()
                .uri("/orders/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Path variable id must be a valid UUID");
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() {
        var userId = new UUID(1L, 1L);
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"00000000-0000-0001-0000-000000000001",
                         "items":[{"productId":"00000000-0000-0002-0000-000000000002","quantity":2}]}
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("USER_NOT_FOUND")
                .jsonPath("$.error.status").isEqualTo(404);

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"00000000-0000-0001-0000-000000000001",
                         "items":[{"productId":"00000000-0000-0002-0000-000000000002","quantity":2}]}
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("PRODUCT_NOT_FOUND")
                .jsonPath("$.error.status").isEqualTo(404);

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void shouldReturnConflictWhenStockIsInsufficient() {
        var userId = new UUID(1L, 1L);
        var productId = new UUID(2L, 2L);
        when(userRepository.findById(userId)).thenReturn(Mono.just(user(userId)));
        when(productRepository.findById(productId)).thenReturn(Mono.just(product(productId)));
        when(productRepository.decrementStockIfAvailable(productId, 2)).thenReturn(Mono.just(false));

        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":"00000000-0000-0001-0000-000000000001",
                         "items":[{"productId":"00000000-0000-0002-0000-000000000002","quantity":2}]}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INSUFFICIENT_STOCK")
                .jsonPath("$.error.status").isEqualTo(409);

        verify(orderRepository, never()).save(any());
        verify(productCachePort, never()).evict(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void shouldReturnBadRequestWhenPayloadIsInvalid() {
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"items":[]}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.details[?(@.field == 'userId')]").exists()
                .jsonPath("$.error.details[?(@.field == 'items')]").exists();
    }

    @Test
    void shouldReturnBadRequestWhenBodyIsMalformed() {
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"userId":
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request body is invalid");
    }

    private User user(UUID id) {
        return new User(id, new Email("buyer@example.com"), "Buyer", "Address");
    }

    private Product product(UUID id) {
        return new Product(id, "Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), 10);
    }
}
