package com.b2chat.ordermanagement.api.validation;

import com.b2chat.ordermanagement.api.error.ApiErrorDetail;
import com.b2chat.ordermanagement.api.exception.ApiStatusException;
import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class RequestValidator {
    private final Validator validator;

    public <T> T validate(T request) {
        var details = validator.validate(request).stream()
                .map(violation -> new ApiErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .sorted(Comparator.comparing(ApiErrorDetail::field)
                        .thenComparing(ApiErrorDetail::message))
                .toList();

        if (!details.isEmpty()) {
            throw new InvalidRequestException("INVALID_REQUEST", "Request validation failed", details);
        }
        return request;
    }

    public Mono<Void> validateJsonContentType(ServerRequest request) {
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
