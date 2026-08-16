package com.b2chat.ordermanagement.r2dbc.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("products")
public class ProductData {
    @Id
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
}
