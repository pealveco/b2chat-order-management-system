package com.b2chat.ordermanagement.api.product;

import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import com.b2chat.ordermanagement.api.response.ApiResponse;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.usecase.createproduct.CreateProductUseCase;
import com.b2chat.ordermanagement.usecase.deleteproduct.DeleteProductUseCase;
import com.b2chat.ordermanagement.usecase.listproducts.ListProductsUseCase;
import com.b2chat.ordermanagement.usecase.updateproduct.UpdateProductUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductHandler {
    private final CreateProductUseCase createProductUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> create(ServerRequest serverRequest) {
        return requestValidator.validateJsonContentType(serverRequest)
                .then(serverRequest.bodyToMono(CreateProductRequest.class)
                        .onErrorMap(error -> new InvalidRequestException("INVALID_REQUEST",
                                "Request body is invalid", List.of())))
                .switchIfEmpty(Mono.error(new InvalidRequestException("EMPTY_BODY",
                        "Request body is required", List.of())))
                .map(requestValidator::validate)
                .flatMap(request -> createProductUseCase.execute(
                        request.name(),
                        request.description(),
                        request.price(),
                        request.stock()))
                .flatMap(product -> ServerResponse.created(URI.create("/products/" + product.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(ProductResponse.from(product), serverRequest.path())));
    }

    public Mono<ServerResponse> list(ServerRequest serverRequest) {
        return listProductsUseCase.execute()
                .map(ProductResponse::from)
                .collectList()
                .flatMap(products -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(products, serverRequest.path())));
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        var id = parseProductId(serverRequest);
        return requestValidator.validateJsonContentType(serverRequest)
                .then(serverRequest.bodyToMono(UpdateProductRequest.class)
                        .onErrorMap(error -> new InvalidRequestException("INVALID_REQUEST",
                                "Request body is invalid", List.of())))
                .switchIfEmpty(Mono.error(new InvalidRequestException("EMPTY_BODY",
                        "Request body is required", List.of())))
                .map(requestValidator::validate)
                .flatMap(request -> updateProductUseCase.execute(
                        id,
                        request.name(),
                        request.description(),
                        request.price(),
                        request.stock()))
                .flatMap(product -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(ProductResponse.from(product), serverRequest.path())));
    }

    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        var id = parseProductId(serverRequest);
        return deleteProductUseCase.execute(id)
                .then(ServerResponse.noContent().build());
    }

    private UUID parseProductId(ServerRequest serverRequest) {
        try {
            return UUID.fromString(serverRequest.pathVariable("id"));
        } catch (IllegalArgumentException error) {
            throw new InvalidRequestException("INVALID_REQUEST",
                    "Path variable id must be a valid UUID", List.of());
        }
    }
}
