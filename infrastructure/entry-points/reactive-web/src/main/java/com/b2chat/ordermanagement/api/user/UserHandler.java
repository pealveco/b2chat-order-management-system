package com.b2chat.ordermanagement.api.user;

import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import com.b2chat.ordermanagement.api.response.ApiResponse;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.usecase.getuser.GetUserUseCase;
import com.b2chat.ordermanagement.usecase.registeruser.RegisterUserUseCase;
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
public class UserHandler {
    private final RegisterUserUseCase registerUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> register(ServerRequest serverRequest) {
        return requestValidator.validateJsonContentType(serverRequest)
                .then(serverRequest.bodyToMono(RegisterUserRequest.class)
                        .onErrorMap(error -> new InvalidRequestException("INVALID_REQUEST",
                                "Request body is invalid", List.of())))
                .switchIfEmpty(Mono.error(new InvalidRequestException("EMPTY_BODY",
                        "Request body is required", List.of())))
                .map(requestValidator::validate)
                .flatMap(request -> registerUserUseCase.execute(request.email(), request.name(), request.address()))
                .flatMap(user -> ServerResponse.created(URI.create("/users/" + user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(UserResponse.from(user), serverRequest.path())));
    }

    public Mono<ServerResponse> getById(ServerRequest serverRequest) {
        return parseUserId(serverRequest.pathVariable("id"))
                .flatMap(getUserUseCase::execute)
                .flatMap(user -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(UserResponse.from(user), serverRequest.path())));
    }

    private Mono<UUID> parseUserId(String id) {
        return Mono.fromCallable(() -> UUID.fromString(id))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new InvalidRequestException("INVALID_REQUEST",
                                "Path variable id must be a valid UUID",
                                List.of()));
    }
}
