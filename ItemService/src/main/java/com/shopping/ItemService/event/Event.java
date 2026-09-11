package com.shopping.ItemService.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    private UUID eventId;
    private String topic;
    private String eventType;
    private Integer schemaVersion;
    private String aggregateId;
    private Long aggregateVersion;
    private Long userId;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer availableQuantity;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant occurredAt;
}
