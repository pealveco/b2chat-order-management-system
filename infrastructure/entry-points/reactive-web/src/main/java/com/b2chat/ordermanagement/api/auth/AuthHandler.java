package com.b2chat.ordermanagement.api.auth;

import com.b2chat.ordermanagement.api.error.ApiErrorDetail;
import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import com.b2chat.ordermanagement.api.response.ApiResponse;
import com.b2chat.ordermanagement.api.validation.RequestValidator;
import com.b2chat.ordermanagement.usecase.issuetoken.IssueTokenUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthHandler {
    private final IssueTokenUseCase issueTokenUseCase;
    private final RequestValidator requestValidator;
    private final long expirationMinutes;

    public AuthHandler(IssueTokenUseCase issueTokenUseCase,
                       RequestValidator requestValidator,
                       @Value("${jwt.expiration-minutes:60}") long expirationMinutes) {
        this.issueTokenUseCase = issueTokenUseCase;
        this.requestValidator = requestValidator;
        this.expirationMinutes = expirationMinutes;
    }

    public Mono<ServerResponse> issueToken(ServerRequest serverRequest) {
        return requestValidator.validateJsonContentType(serverRequest)
                .then(serverRequest.bodyToMono(IssueTokenRequest.class)
                        .onErrorMap(error -> new InvalidRequestException("INVALID_REQUEST",
                                "Request body is invalid", List.of())))
                .switchIfEmpty(Mono.error(new InvalidRequestException("EMPTY_BODY",
                        "Request body is required", List.of())))
                .map(requestValidator::validate)
                .map(this::validateIdentifier)
                .flatMap(request -> issueTokenUseCase.execute(request.userId(), request.email()))
                .flatMap(token -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.success(TokenResponse.bearer(token, expirationMinutes * 60),
                                serverRequest.path())));
    }

    private IssueTokenRequest validateIdentifier(IssueTokenRequest request) {
        var hasUserId = request.userId() != null;
        var hasEmail = request.email() != null && !request.email().trim().isEmpty();

        if (hasUserId == hasEmail) {
            throw new InvalidRequestException("INVALID_REQUEST",
                    "Request must include exactly one identifier: userId or email",
                    List.of(new ApiErrorDetail("userId|email",
                            "exactly one of userId or email is required")));
        }

        return request;
    }
}
