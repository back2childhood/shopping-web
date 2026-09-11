package com.shopping.ItemService.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("items_by_id")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemProjection {
    @Id
    private String id;
    private Long userId;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
