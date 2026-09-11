package com.shopping.ItemService.event;

import com.alibaba.fastjson2.JSONObject;
import com.shopping.ItemService.dao.ItemProjectionRepository;
import com.shopping.ItemService.entity.ItemProjection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {
    private final ItemProjectionRepository itemProjectionRepository;
    private final CacheManager cacheManager;

    public EventConsumer(ItemProjectionRepository itemProjectionRepository, CacheManager cacheManager) {
        this.itemProjectionRepository = itemProjectionRepository;
        this.cacheManager = cacheManager;
    }

    @KafkaListener(
            topics = "${app.kafka.item-topic:item.events.v1}",
            groupId = "${spring.kafka.consumer.group-id:item-service-projection-v1}"
    )
    public void consume(String message) {
        Event event = JSONObject.parseObject(message, Event.class);
        if (event == null || event.getEventId() == null || event.getEventType() == null) {
            throw new IllegalArgumentException("Invalid item event");
        }

        EventType eventType = EventType.valueOf(event.getEventType());
        switch (eventType) {
            case ITEM_CREATED, ITEM_UPDATED -> upsertProjection(event);
            case ITEM_DELETED -> itemProjectionRepository.deleteById(event.getAggregateId());
            case INVENTORY_CHANGED -> {
                // Inventory remains strongly consistent in PostgreSQL. This event invalidates read caches.
            }
        }
        evictCaches(event);
    }

    private void upsertProjection(Event event) {
        ItemProjection existing = itemProjectionRepository.findById(event.getAggregateId()).orElse(null);
        if (existing != null && existing.getVersion() != null
                && existing.getVersion() >= event.getAggregateVersion()) {
            return;
        }

        itemProjectionRepository.save(ItemProjection.builder()
                .id(event.getAggregateId())
                .userId(event.getUserId())
                .name(event.getName())
                .description(event.getDescription())
                .price(event.getPrice())
                .currency(event.getCurrency())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .version(event.getAggregateVersion())
                .build());
    }

    private void evictCaches(Event event) {
        Cache itemCache = cacheManager.getCache("items");
        if (itemCache != null) {
            itemCache.evict(event.getAggregateId());
        }

        Cache itemListCache = cacheManager.getCache("itemList");
        if (itemListCache != null) {
            itemListCache.clear();
        }

        Cache sellerItemsCache = cacheManager.getCache("sellerItems");
        if (sellerItemsCache != null && event.getUserId() != null) {
            sellerItemsCache.evict(event.getUserId());
        }
    }
}
