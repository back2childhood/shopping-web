package com.shopping.OrderService.client.dto;

import lombok.Data;

@Data
public class ItemDTO {
    private String id;
    private String name;
    private Double price;
    private Integer stock;
}