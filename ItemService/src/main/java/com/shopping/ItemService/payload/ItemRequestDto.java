package com.shopping.ItemService.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemRequestDto {
    @NotBlank
    private String name;
    private String description;
    @NotNull @DecimalMin("0.00")
    private BigDecimal price;
    @NotBlank
    private String currency;
    @NotNull @Min(0)
    private Integer stock;
}
