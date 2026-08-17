package com.b2chat.ordermanagement.api.order;

import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import com.b2chat.ordermanagement.api.response.ApiResponse;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.usecase.placeorder.PlaceOrderItemCommand;
import com.b2chat.ordermanagement.usecase.placeorder.PlaceOrderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderHandler {
    private final PlaceOrderUseCase placeOrderUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> place(ServerRequest serverRequest) {
        return requestValidator.validateJsonContentType(serverRequest)
                .then(serverRequest.bodyToMono(PlaceOrderRequest.class)
                        .onErrorMap(error -> new InvalidRequestException("INVALID_REQUEST",
                                "Request body is invalid", List.of())))
                .switchIfEmpty(Mono.error(new InvalidRequestException("EMPTY_BODY",
                        "Request body is required", List.of())))
                .map(requestValidator::validate)
                .flatMap(request -> placeOrderUseCase.execute(request.userId(), toCommands(request)))
                .flatMap(order -> ServerResponse.accepted()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(OrderResponse.from(order), serverRequest.path())));
    }

    private List<PlaceOrderItemCommand> toCommands(PlaceOrderRequest request) {
        return request.items().stream()
                .map(item -> new PlaceOrderItemCommand(item.productId(), item.quantity()))
                .toList();
    }
}
