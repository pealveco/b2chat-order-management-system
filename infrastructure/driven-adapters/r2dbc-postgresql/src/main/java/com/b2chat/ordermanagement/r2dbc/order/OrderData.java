package com.b2chat.ordermanagement.r2dbc.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("orders")
public class OrderData {
    @Id
    private UUID id;
    @Column("user_id")
    private UUID userId;
    private String status;
    @Column("created_at")
    private Instant createdAt;
}
