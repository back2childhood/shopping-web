package com.shopping.OrderService.event;

import lombok.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
//@Setter
@Getter
public class Event {
    private String topic;
    private Long orderId;
    private Long userId;
    private List<ItemQuantity> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemQuantity {
        private String itemId;
        private double price;
        private Integer quantity;
    }

    public Event setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public Event setOrderId(Long orderId) {
        this.orderId = orderId;
        return this;
    }

    public Event setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Event setItems(List<ItemQuantity> items) {
        this.items = items;
        return this;
    }
}