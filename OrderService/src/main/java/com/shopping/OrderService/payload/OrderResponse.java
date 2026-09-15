package com.shopping.OrderService.payload;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

@Data
public class OrderResponse {
    private Long id;
    private Long userId;
    private BigDecimal totalPrice;
    private String currency;
    private OrderStatus status;
    private Instant createdAt;
    private List<OrderItemResponse> items;

    @Data
    public static class OrderItemResponse {
        private String itemId;
        private Integer quantity;
        private BigDecimal price;
        private String itemName; // You might want to include item details
    }
}
