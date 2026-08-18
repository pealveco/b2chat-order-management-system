package com.b2chat.ordermanagement.r2dbc.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("order_items")
public class OrderItemData {
    @Id
    private UUID id;
    @Column("order_id")
    private UUID orderId;
    @Column("product_id")
    private UUID productId;
    private Integer quantity;
    @Column("unit_price_at_order_time")
    private BigDecimal unitPriceAtOrderTime;
}
