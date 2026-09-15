package com.shopping.OrderService.client.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemDTO {
    private String id;
    private String name;
    private BigDecimal price;
    private String currency;
    private Integer stock;
}
