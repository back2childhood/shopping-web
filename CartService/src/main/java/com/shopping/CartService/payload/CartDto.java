package com.shopping.CartService.payload;

import lombok.Data;

import java.util.List;

@Data
public class CartDto {
    private Long userId;
    private List<CartItemDto> items;
    private double totalPrice;

    @Data
    public static class CartItemDto {
        private String itemId;
        private int quantity;
        private double price;
    }
}