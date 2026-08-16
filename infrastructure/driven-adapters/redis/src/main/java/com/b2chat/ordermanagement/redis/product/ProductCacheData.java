package com.b2chat.ordermanagement.redis.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCacheData {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
}
