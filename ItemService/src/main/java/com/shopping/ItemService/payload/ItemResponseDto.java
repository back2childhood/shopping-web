package com.shopping.ItemService.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponseDto {
    private String id;
    private Long userId;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer stock;
}
