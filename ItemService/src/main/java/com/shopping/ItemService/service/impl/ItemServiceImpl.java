package com.shopping.ItemService.service.impl;

import com.shopping.ItemService.dao.InventoryRepository;
import com.shopping.ItemService.dao.ItemProjectionRepository;
import com.shopping.ItemService.dao.ItemRepository;
import com.shopping.ItemService.entity.Inventory;
import com.shopping.ItemService.entity.Item;
import com.shopping.ItemService.entity.ItemProjection;
import com.shopping.ItemService.event.Event;
import com.shopping.ItemService.event.EventType;
import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;
import com.shopping.ItemService.service.ItemService;
import com.shopping.ItemService.service.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final ItemProjectionRepository itemProjectionRepository;
    private final InventoryRepository inventoryRepository;
    private final OutboxService outboxService;
    private final String itemTopic;

    public ItemServiceImpl(
            ItemRepository itemRepository,
            ItemProjectionRepository itemProjectionRepository,
            InventoryRepository inventoryRepository,
            OutboxService outboxService,
            @Value("${app.kafka.item-topic:item.events.v1}") String itemTopic) {
        this.itemRepository = itemRepository;
        this.itemProjectionRepository = itemProjectionRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxService = outboxService;
        this.itemTopic = itemTopic;
    }

    @Override
    @Transactional
    @CachePut(value = "items", key = "#result.id")
    @Caching(evict = {
            @CacheEvict(value = "itemList", allEntries = true),
            @CacheEvict(value = "sellerItems", key = "#userId")
    })
    public ItemResponseDto createItem(ItemRequestDto dto, Long userId) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Item item = Item.builder()
                .id(id)
                .userId(userId)
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .currency(dto.getCurrency().toUpperCase())
                .createdAt(now)
                .updatedAt(now)
                .build();
        item = itemRepository.saveAndFlush(item);

        Inventory inventory = inventoryRepository.save(new Inventory(id, dto.getStock(), 0L));
        outboxService.enqueue(toEvent(EventType.ITEM_CREATED, item, inventory));
        return toResponse(item, inventory.getAvailableQuantity());
    }

    @Override
    @Cacheable(value = "items", key = "#id")
    public ItemResponseDto getItemById(String id) {
        ItemProjection projection = itemProjectionRepository.findById(id).orElse(null);
        if (projection != null) {
            return toResponse(projection, requireInventory(id).getAvailableQuantity());
        }

        Item item = requireItem(id);
        return toResponse(item, requireInventory(id).getAvailableQuantity());
    }

    @Override
    @Cacheable(value = "itemList")
    public List<ItemResponseDto> getAllItems() {
        List<ItemProjection> projections = new ArrayList<>(itemProjectionRepository.findAll());
        if (projections.isEmpty() && itemRepository.count() > 0) {
            return itemRepository.findAll().stream()
                    .map(item -> toResponse(item, inventoryQuantity(item.getId())))
                    .toList();
        }
        return projections.stream()
                .map(item -> toResponse(item, inventoryQuantity(item.getId())))
                .toList();
    }

    @Override
    @Cacheable(value = "sellerItems", key = "#userId")
    public List<ItemResponseDto> getItemsByUserId(Long userId) {
        return itemRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(item -> toResponse(item, inventoryQuantity(item.getId())))
                .toList();
    }

    @Override
    @Transactional
    @Caching(
            put = @CachePut(value = "items", key = "#id"),
            evict = {
                    @CacheEvict(value = "itemList", allEntries = true),
                    @CacheEvict(value = "sellerItems", key = "#userId")
            }
    )
    public ItemResponseDto updateItem(String id, ItemRequestDto dto, Long userId) {
        Item existing = requireOwnedItem(id, userId);
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setPrice(dto.getPrice());
        existing.setCurrency(dto.getCurrency().toUpperCase());
        existing.setUpdatedAt(Instant.now());
        existing = itemRepository.saveAndFlush(existing);

        Inventory inventory = inventoryRepository.findById(id)
                .orElseGet(() -> new Inventory(id, dto.getStock(), 0L));
        inventory = inventoryRepository.save(new Inventory(id, dto.getStock(), inventory.getVersion() + 1));
        outboxService.enqueue(toEvent(EventType.ITEM_UPDATED, existing, inventory));
        return toResponse(existing, inventory.getAvailableQuantity());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true),
            @CacheEvict(value = "sellerItems", key = "#userId")
    })
    public void deleteItem(String id, Long userId) {
        Item item = requireOwnedItem(id, userId);
        Inventory inventory = requireInventory(id);
        Event event = toEvent(EventType.ITEM_DELETED, item, inventory);
        event.setAggregateVersion(item.getVersion() + 1);
        outboxService.enqueue(event);
        inventoryRepository.deleteById(id);
        itemRepository.delete(item);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true),
            @CacheEvict(value = "sellerItems", allEntries = true)
    })
    public ItemResponseDto increaseStock(String id, int quantity) {
        requirePositive(quantity);
        Item item = requireItem(id);
        if (inventoryRepository.release(id, quantity) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found");
        }
        Inventory inventory = requireInventory(id);
        outboxService.enqueue(toEvent(EventType.INVENTORY_CHANGED, item, inventory));
        return toResponse(item, inventory.getAvailableQuantity());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true),
            @CacheEvict(value = "sellerItems", allEntries = true)
    })
    public ItemResponseDto decreaseStock(String id, int quantity) {
        requirePositive(quantity);
        Item item = requireItem(id);
        if (inventoryRepository.reserve(id, quantity) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
        }
        Inventory inventory = requireInventory(id);
        outboxService.enqueue(toEvent(EventType.INVENTORY_CHANGED, item, inventory));
        return toResponse(item, inventory.getAvailableQuantity());
    }

    private Item requireItem(String id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private Item requireOwnedItem(String id, Long userId) {
        Item item = requireItem(id);
        if (!item.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own products");
        }
        return item;
    }

    private Inventory requireInventory(String id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));
    }

    private int inventoryQuantity(String id) {
        return inventoryRepository.findById(id)
                .map(Inventory::getAvailableQuantity)
                .orElse(0);
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
    }

    private Event toEvent(EventType eventType, Item item, Inventory inventory) {
        return Event.builder()
                .eventId(UUID.randomUUID())
                .topic(itemTopic)
                .eventType(eventType.name())
                .schemaVersion(1)
                .aggregateId(item.getId())
                .aggregateVersion(eventType == EventType.INVENTORY_CHANGED
                        ? inventory.getVersion() : item.getVersion())
                .userId(item.getUserId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .currency(item.getCurrency())
                .availableQuantity(inventory.getAvailableQuantity())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .occurredAt(Instant.now())
                .build();
    }

    private ItemResponseDto toResponse(Item item, int stock) {
        return new ItemResponseDto(item.getId(), item.getUserId(), item.getName(), item.getDescription(),
                item.getPrice(), item.getCurrency(), stock);
    }

    private ItemResponseDto toResponse(ItemProjection item, int stock) {
        return new ItemResponseDto(item.getId(), item.getUserId(), item.getName(), item.getDescription(),
                item.getPrice(), item.getCurrency(), stock);
    }
}
