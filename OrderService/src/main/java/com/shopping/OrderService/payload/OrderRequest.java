package com.shopping.OrderService.payload;


import lombok.Data;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.List;

@Data
public class OrderRequest {

    private Long userId;
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
        private String itemId;
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
