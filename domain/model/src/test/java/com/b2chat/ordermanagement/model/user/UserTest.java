package com.b2chat.ordermanagement.model.user;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.email.Email;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {
    @Test
    void shouldCreateUserPendingDatabaseGeneratedIdAndTrimmedFields() {
        var user = User.create(new Email("juan@example.com"), " Juan Perez ", " Cra 10 ");

        assertNull(user.getId());
        assertEquals("juan@example.com", user.getEmail().getValue());
        assertEquals("Juan Perez", user.getName());
        assertEquals("Cra 10", user.getAddress());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(RequiredFieldException.class,
                () -> User.create(new Email("juan@example.com"), " ", "Cra 10"));
    }

    @Test
    void shouldRejectBlankAddress() {
        assertThrows(RequiredFieldException.class,
                () -> User.create(new Email("juan@example.com"), "Juan Perez", ""));
    }
}
