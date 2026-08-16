package com.b2chat.ordermanagement.api.validation;

import com.b2chat.ordermanagement.api.error.ApiErrorDetail;
import com.b2chat.ordermanagement.api.exception.InvalidRequestException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
