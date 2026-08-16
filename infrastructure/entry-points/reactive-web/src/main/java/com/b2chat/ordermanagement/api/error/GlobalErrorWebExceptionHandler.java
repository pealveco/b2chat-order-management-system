package com.b2chat.ordermanagement.api.error;

import com.b2chat.ordermanagement.model.common.DomainException;
import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.user.EmailAlreadyExistsException;
import com.b2chat.ordermanagement.api.exception.ApiStatusException;
import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Log4j2
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {
    private static final String INTERNAL_ERROR = "INTERNAL_SERVER_ERROR";
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(throwable);
        }

        var status = resolveStatus(throwable);
        var error = resolveError(throwable, status, exchange.getRequest().getPath().value());

        if (status.is5xxServerError()) {
            log.error("Unhandled request error", throwable);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            var bytes = objectMapper.writeValueAsBytes(new ApiErrorResponse(error));
            var buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (RuntimeException serializationError) {
            return Mono.error(serializationError);
        }
    }

    private HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof EmailAlreadyExistsException) {
            return HttpStatus.CONFLICT;
        }
        if (throwable instanceof RepositoryUnavailableException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (throwable instanceof ApiStatusException apiStatusException) {
            return apiStatusException.getStatus();
        }
        if (throwable instanceof DomainException || throwable instanceof InvalidRequestException
                || throwable instanceof DecodingException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (throwable instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ApiError resolveError(Throwable throwable, HttpStatus status, String path) {
        if (throwable instanceof InvalidRequestException invalidRequestException) {
            return ApiError.of(invalidRequestException.getCode(), invalidRequestException.getMessage(),
                    status.value(), path, invalidRequestException.getDetails());
        }
        if (throwable instanceof ApiStatusException apiStatusException) {
            return ApiError.of(apiStatusException.getCode(), apiStatusException.getMessage(),
                    status.value(), path);
        }
        if (throwable instanceof RepositoryUnavailableException repositoryUnavailableException) {
            return ApiError.of("SERVICE_UNAVAILABLE", repositoryUnavailableException.getMessage(),
                    status.value(), path);
        }
        if (throwable instanceof DomainException domainException) {
            return ApiError.of(domainException.getCode(), domainException.getMessage(), status.value(), path);
        }
        if (throwable instanceof DecodingException) {
            return ApiError.of("INVALID_REQUEST", "Request body is invalid", status.value(), path);
        }
        if (throwable instanceof ResponseStatusException responseStatusException) {
            var message = responseStatusException.getReason() == null
                    ? status.getReasonPhrase()
                    : responseStatusException.getReason();
            return ApiError.of(status.name(), message, status.value(), path);
        }
        return ApiError.of(INTERNAL_ERROR, "An unexpected error occurred", status.value(), path, List.of());
    }
}
