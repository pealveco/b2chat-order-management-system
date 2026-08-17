package com.b2chat.ordermanagement.api.product;

import com.b2chat.ordermanagement.api.error.GlobalErrorWebExceptionHandler;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import com.b2chat.ordermanagement.usecase.createproduct.CreateProductUseCase;
import com.b2chat.ordermanagement.usecase.deleteproduct.DeleteProductUseCase;
import com.b2chat.ordermanagement.usecase.listproducts.ListProductsUseCase;
import com.b2chat.ordermanagement.usecase.updateproduct.UpdateProductUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
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
        var listProductsUseCase = new ListProductsUseCase(productRepository, productCachePort);
        var updateProductUseCase = new UpdateProductUseCase(productRepository, productCachePort);
        var deleteProductUseCase = new DeleteProductUseCase(productRepository, productCachePort);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var handler = new ProductHandler(createProductUseCase, listProductsUseCase,
                updateProductUseCase, deleteProductUseCase, new RequestValidator(validator));
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

    @Test
    void shouldListProductsFromCache() {
        var product = new Product(new UUID(1L, 1L), "Keyboard", "Mechanical keyboard",
                new com.b2chat.ordermanagement.model.money.Money(new BigDecimal("25.50")), 10);
        when(productCachePort.getAll()).thenReturn(Flux.just(product));

        webTestClient.get()
                .uri("/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo(product.getId().toString())
                .jsonPath("$.data[0].name").isEqualTo("Keyboard")
                .jsonPath("$.data[0].description").isEqualTo("Mechanical keyboard")
                .jsonPath("$.data[0].price").isEqualTo(25.50)
                .jsonPath("$.data[0].stock").isEqualTo(10)
                .jsonPath("$.meta.path").isEqualTo("/products")
                .jsonPath("$.meta.timestamp").exists();

        verify(productRepository, never()).findAll();
    }

    @Test
    void shouldListProductsFromRepositoryAndPopulateCacheWhenCacheIsEmpty() {
        var products = List.of(new Product(new UUID(1L, 1L), "Keyboard", "Mechanical keyboard",
                new com.b2chat.ordermanagement.model.money.Money(new BigDecimal("25.50")), 10));
        when(productCachePort.getAll()).thenReturn(Flux.empty());
        when(productRepository.findAll()).thenReturn(Flux.fromIterable(products));
        when(productCachePort.putAll(products)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo(products.getFirst().getId().toString())
                .jsonPath("$.data[0].name").isEqualTo("Keyboard")
                .jsonPath("$.meta.path").isEqualTo("/products");

        verify(productRepository).findAll();
        verify(productCachePort).putAll(products);
    }

    @Test
    void shouldReturnEmptyListWhenNoProductsExist() {
        when(productCachePort.getAll()).thenReturn(Flux.empty());
        when(productRepository.findAll()).thenReturn(Flux.empty());
        when(productCachePort.putAll(List.of())).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(0)
                .jsonPath("$.meta.path").isEqualTo("/products");
    }

    @Test
    void shouldUpdateProduct() {
        var id = new UUID(1L, 1L);
        var existingProduct = new Product(id, "Keyboard", "Mechanical keyboard",
                new com.b2chat.ordermanagement.model.money.Money(new BigDecimal("25.50")), 10);
        when(productRepository.findById(id)).thenReturn(Mono.just(existingProduct));
        when(productRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Product.class)));
        when(productCachePort.put(any())).thenReturn(Mono.empty());

        webTestClient.put()
                .uri("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Mouse","description":"Wireless mouse","price":15.75,"stock":25}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(id.toString())
                .jsonPath("$.data.name").isEqualTo("Mouse")
                .jsonPath("$.data.description").isEqualTo("Wireless mouse")
                .jsonPath("$.data.price").isEqualTo(15.75)
                .jsonPath("$.data.stock").isEqualTo(25)
                .jsonPath("$.meta.path").isEqualTo("/products/" + id);

        verify(productRepository).findById(id);
        verify(productRepository).save(any());
        verify(productCachePort).put(any());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingProduct() {
        var id = new UUID(1L, 1L);
        when(productRepository.findById(id)).thenReturn(Mono.empty());

        webTestClient.put()
                .uri("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Mouse","description":"Wireless mouse","price":15.75,"stock":25}
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("PRODUCT_NOT_FOUND")
                .jsonPath("$.error.message").isEqualTo("Product with id " + id + " was not found")
                .jsonPath("$.error.status").isEqualTo(404);

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldReturnBadRequestWhenProductIdIsInvalid() {
        webTestClient.put()
                .uri("/products/not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Mouse","description":"Wireless mouse","price":15.75,"stock":25}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Path variable id must be a valid UUID")
                .jsonPath("$.error.status").isEqualTo(400);

        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldReturnBadRequestWhenUpdatePayloadIsInvalid() {
        var id = new UUID(1L, 1L);

        webTestClient.put()
                .uri("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"","description":"Wireless mouse","price":0,"stock":-1}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Request validation failed")
                .jsonPath("$.error.details[?(@.field == 'name')]").exists()
                .jsonPath("$.error.details[?(@.field == 'price')]").exists()
                .jsonPath("$.error.details[?(@.field == 'stock')]").exists();

        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldNotUpdateCacheWhenUpdatePersistenceFails() {
        var id = new UUID(1L, 1L);
        var existingProduct = new Product(id, "Keyboard", "Mechanical keyboard",
                new com.b2chat.ordermanagement.model.money.Money(new BigDecimal("25.50")), 10);
        when(productRepository.findById(id)).thenReturn(Mono.just(existingProduct));
        when(productRepository.save(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException(
                        "Product repository is temporarily unavailable")));

        webTestClient.put()
                .uri("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Mouse","description":"Wireless mouse","price":15.75,"stock":25}
                        """)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.error.message").isEqualTo("Product repository is temporarily unavailable");

        verify(productRepository).findById(id);
        verify(productRepository).save(any());
        verify(productCachePort, never()).put(any());
    }

    @Test
    void shouldDeleteProduct() {
        var id = new UUID(1L, 1L);
        var product = new Product(id, "Keyboard", "Mechanical keyboard",
                new com.b2chat.ordermanagement.model.money.Money(new BigDecimal("25.50")), 10);
        when(productRepository.findById(id)).thenReturn(Mono.just(product));
        when(productRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Product.class)));
        when(productCachePort.evict(id)).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/products/{id}", id)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(productRepository).findById(id);
        verify(productRepository).save(Mockito.argThat(saved -> !saved.getActive()));
        verify(productCachePort).evict(id);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingProduct() {
        var id = new UUID(1L, 1L);
        when(productRepository.findById(id)).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/products/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("PRODUCT_NOT_FOUND")
                .jsonPath("$.error.message").isEqualTo("Product with id " + id + " was not found")
                .jsonPath("$.error.status").isEqualTo(404);

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).evict(any());
    }

    @Test
    void shouldReturnBadRequestWhenDeletingProductIdIsInvalid() {
        webTestClient.delete()
                .uri("/products/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.error.message").isEqualTo("Path variable id must be a valid UUID")
                .jsonPath("$.error.status").isEqualTo(400);

        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
        verify(productCachePort, never()).evict(any());
    }

    @Test
    void shouldNotEvictCacheWhenDeletePersistenceFails() {
        var id = new UUID(1L, 1L);
        var product = new Product(id, "Keyboard", "Mechanical keyboard",
                new com.b2chat.ordermanagement.model.money.Money(new BigDecimal("25.50")), 10);
        when(productRepository.findById(id)).thenReturn(Mono.just(product));
        when(productRepository.save(any()))
                .thenReturn(Mono.error(new RepositoryUnavailableException(
                        "Product repository is temporarily unavailable")));

        webTestClient.delete()
                .uri("/products/{id}", id)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.error.message").isEqualTo("Product repository is temporarily unavailable");

        verify(productRepository).findById(id);
        verify(productRepository).save(any());
        verify(productCachePort, never()).evict(any());
    }
}
