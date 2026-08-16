package com.b2chat.ordermanagement.model.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {
    @Test
    void shouldNormalizeValidEmail() {
        var email = new Email("  JUAN@example.COM ");

        assertEquals("juan@example.com", email.getValue());
    }

    @Test
    void shouldRejectInvalidEmail() {
        assertThrows(InvalidEmailException.class, () -> new Email("invalid-email"));
    }
}
