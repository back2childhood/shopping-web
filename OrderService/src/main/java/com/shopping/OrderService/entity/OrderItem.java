package com.shopping.OrderService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@UserDefinedType("order_item")
public class OrderItem {
    private String itemId;
//    private String itemName;
    private Integer quantity;
    private Double price;
}
