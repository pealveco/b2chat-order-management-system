package com.b2chat.ordermanagement.api.product;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import com.b2chat.ordermanagement.usecase.createproduct.CreateProductUseCase;
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

class ProductRouterRestTest {
    private ProductRepository productRepository;
    private ProductCachePort productCachePort;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        productCachePort = Mockito.mock(ProductCachePort.class);
        var createProductUseCase = new CreateProductUseCase(productRepository, productCachePort);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var handler = new ProductHandler(createProductUseCase, new RequestValidator(validator));
        var router = new ProductRouterRest().productRoutes(handler);
        var handlerStrategies = HandlerStrategies.builder()
                .exceptionHandler(new GlobalErrorWebExceptionHandler(new ObjectMapper()))
                .build();
        webTestClient = WebTestClient.bindToRouterFunction(router)
                .handlerStrategies(handlerStrategies)
                .build();
    }

    @Test
    void shouldCreateProduct() {
        when(productRepository.save(any())).thenAnswer(invocation -> {
            var product = invocation.getArgument(0, Product.class);
            return Mono.just(new Product(new UUID(1L, 1L), product.getName(), product.getDescription(),
                    product.getPrice(), product.getStock()));
        });
        when(productCachePort.put(any())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Keyboard","description":"Mechanical keyboard","price":25.50,"stock":10}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.data.id").exists()
                .jsonPath("$.data.name").isEqualTo("Keyboard")
                .jsonPath("$.data.description").isEqualTo("Mechanical keyboard")
                .jsonPath("$.data.price").isEqualTo(25.50)
                .jsonPath("$.data.stock").isEqualTo(10)
                .jsonPath("$.meta.path").isEqualTo("/products")
                .jsonPath("$.meta.timestamp").exists();
    }

    @Test
    void shouldReturnBadRequestWhenPriceIsNotPositive() {
        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Keyboard","description":"Mechanical keyboard","price":0,"stock":10}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request validation failed")
                .jsonPath("$.error.status").isEqualTo(400)
                .jsonPath("$.error.details[?(@.field == 'price')].message")
                .isEqualTo("price must be greater than zero");

        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldReturnBadRequestWhenStockIsNegative() {
        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Keyboard","description":"Mechanical keyboard","price":25.50,"stock":-1}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request validation failed")
                .jsonPath("$.error.status").isEqualTo(400)
                .jsonPath("$.error.details[?(@.field == 'stock')].message")
                .isEqualTo("stock must be greater than or equal to zero");

        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsAreMissing() {
        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"description":"Mechanical keyboard"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.details[?(@.field == 'name')]").exists()
                .jsonPath("$.error.details[?(@.field == 'price')]").exists()
                .jsonPath("$.error.details[?(@.field == 'stock')]").exists();
    }

    @Test
    void shouldReturnBadRequestWhenBodyIsMalformed() {
        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":
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
                .uri("/products")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("name=Keyboard")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE")
                .jsonPath("$.error.message").isEqualTo("Content-Type must be application/json")
                .jsonPath("$.error.status").isEqualTo(415);
    }

    @Test
    void shouldReturnServiceUnavailableWhenPersistenceFails() {
        when(productRepository.save(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException(
                        "Product repository is temporarily unavailable")));

        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Keyboard","description":"Mechanical keyboard","price":25.50,"stock":10}
                        """)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.error.message").isEqualTo("Product repository is temporarily unavailable")
                .jsonPath("$.error.status").isEqualTo(503);
    }

    @Test
    void shouldReturnServiceUnavailableWhenCacheFails() {
        when(productRepository.save(any())).thenAnswer(invocation -> {
            var product = invocation.getArgument(0, Product.class);
            return Mono.just(new Product(new UUID(1L, 1L), product.getName(), product.getDescription(),
                    product.getPrice(), product.getStock()));
        });
        when(productCachePort.put(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException("Product cache is temporarily unavailable")));

        webTestClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Keyboard","description":"Mechanical keyboard","price":25.50,"stock":10}
                        """)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.error.message").isEqualTo("Product cache is temporarily unavailable")
                .jsonPath("$.error.status").isEqualTo(503);
    }
}
