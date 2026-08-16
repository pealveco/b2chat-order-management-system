package com.b2chat.ordermanagement.api.user;

import com.b2chat.ordermanagement.api.exception.ApiStatusException;
import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import com.b2chat.ordermanagement.api.response.ApiResponse;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.usecase.registeruser.RegisterUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserHandler {
    private final RegisterUserUseCase registerUserUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> register(ServerRequest serverRequest) {
        return validateContentType(serverRequest)
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

    private Mono<Void> validateContentType(ServerRequest request) {
        return Mono.defer(() -> {
            var contentType = request.headers().firstHeader(HttpHeaders.CONTENT_TYPE);
            try {
                var mediaType = contentType == null ? null : MediaType.parseMediaType(contentType);
                if (mediaType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
                    return Mono.error(new ApiStatusException("UNSUPPORTED_MEDIA_TYPE",
                            "Content-Type must be application/json", HttpStatus.UNSUPPORTED_MEDIA_TYPE));
                }
            } catch (InvalidMediaTypeException invalidMediaTypeException) {
                return Mono.error(new ApiStatusException("UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type must be application/json", HttpStatus.UNSUPPORTED_MEDIA_TYPE));
            }
            return Mono.empty();
        });
    }
}
