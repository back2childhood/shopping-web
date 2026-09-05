package com.shopping.OrderService.payload;


import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Data
public class OrderRequest {

    @NotNull
    private Long userId;
    @NotEmpty @Valid
    private List<OrderItemRequest> items;

    @Override
    public String toString() {
        return "OrderRequest{" +
                "userId=" + userId +
                ", items=" + (items != null ? items.toString() : "null") +
                '}';
    }

    @Data
    public static class OrderItemRequest {
        @NotNull
        private String itemId;
        @NotNull @Positive
        private Integer quantity;

        @Override
        public String toString() {
            return "OrderItemRequest{" +
                    "itemId='" + itemId + '\'' +
                    ", quantity=" + quantity +
                    '}';
        }
    }
}
