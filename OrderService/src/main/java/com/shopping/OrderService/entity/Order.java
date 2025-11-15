package com.shopping.OrderService.entity;

import com.shopping.OrderService.payload.OrderStatus;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.Indexed;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;


@Data
@Table("orders")
public class Order implements Serializable {
    @PrimaryKey
    private Long id;
    @Indexed
    private Long userId;
    private Double totalPrice;
    private OrderStatus status;
    private Instant createdAt;
    private List<OrderItem> items;
}