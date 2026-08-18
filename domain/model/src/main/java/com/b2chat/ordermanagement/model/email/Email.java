package com.b2chat.ordermanagement.model.email;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Locale;
import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public final class Email {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final String value;

    public Email(String value) {
        var normalizedValue = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedValue).matches()) {
            throw new InvalidEmailException();
        }
        this.value = normalizedValue;
    }

    @Override
    public String toString() {
        return value;
    }
}
