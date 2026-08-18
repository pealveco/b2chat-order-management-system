package com.b2chat.ordermanagement.model.product;

import com.b2chat.ordermanagement.model.common.RequiredFieldException;
import com.b2chat.ordermanagement.model.money.InvalidMoneyException;
import com.b2chat.ordermanagement.model.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {
    @Test
    void shouldCreateProductPendingDatabaseGeneratedIdAndTrimmedFields() {
        var product = Product.create(" Keyboard ", " Mechanical keyboard ", new Money(new BigDecimal("25.50")), 10);

        assertNull(product.getId());
        assertEquals("Keyboard", product.getName());
        assertEquals("Mechanical keyboard", product.getDescription());
        assertEquals(new BigDecimal("25.50"), product.getPrice().getAmount());
        assertEquals(10, product.getStock());
        assertTrue(product.getActive());
    }

    @Test
    void shouldDeactivateProduct() {
        var id = UUID.randomUUID();
        var product = new Product(id, "Keyboard", "Mechanical keyboard", new Money(new BigDecimal("25.50")), 10);

        var inactiveProduct = product.deactivate();

        assertEquals(id, inactiveProduct.getId());
        assertEquals("Keyboard", inactiveProduct.getName());
        assertFalse(inactiveProduct.getActive());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(RequiredFieldException.class,
                () -> Product.create(" ", "Description", new Money(new BigDecimal("25.50")), 10));
    }

    @Test
    void shouldRejectNegativeStock() {
        assertThrows(InvalidProductStockException.class,
                () -> Product.create("Keyboard", "Description", new Money(new BigDecimal("25.50")), -1));
    }

    @Test
    void shouldRejectNullPrice() {
        assertThrows(InvalidMoneyException.class,
                () -> Product.create("Keyboard", "Description", null, 10));
    }
}
