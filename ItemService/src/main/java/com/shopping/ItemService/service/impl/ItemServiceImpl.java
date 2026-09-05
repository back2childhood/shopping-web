package com.shopping.ItemService.service.impl;

import com.shopping.ItemService.dao.ItemRepository;
import com.shopping.ItemService.entity.Item;
import com.shopping.ItemService.inventory.Inventory;
import com.shopping.ItemService.inventory.InventoryRepository;
import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;
import com.shopping.ItemService.service.ItemService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final InventoryRepository inventoryRepository;

    public ItemServiceImpl(ItemRepository itemRepository, InventoryRepository inventoryRepository) {
        this.itemRepository = itemRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @CachePut(value = "items", key = "#result.id")
    @CacheEvict(value = "itemList", allEntries = true)
    public ItemResponseDto createItem(ItemRequestDto dto) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Item item = new Item(id, dto.getName(), dto.getDescription(), dto.getPrice(),
                dto.getCurrency().toUpperCase(), now, now);
        itemRepository.save(item);
        inventoryRepository.save(new Inventory(id, dto.getStock(), 0L));
        return toResponse(item, dto.getStock());
    }

    @Override
    @Cacheable(value = "items", key = "#id")
    public ItemResponseDto getItemById(String id) {
        Item item = requireItem(id);
        return toResponse(item, requireInventory(id).getAvailableQuantity());
    }

    @Override
    @Cacheable(value = "itemList")
    public List<ItemResponseDto> getAllItems() {
        return new ArrayList<>(itemRepository.findAll().stream()
                .map(item -> toResponse(item, inventoryRepository.findById(item.getId())
                        .map(Inventory::getAvailableQuantity).orElse(0)))
                .toList());
    }

    @Override
    @Caching(put = @CachePut(value = "items", key = "#id"),
            evict = @CacheEvict(value = "itemList", allEntries = true))
    public ItemResponseDto updateItem(String id, ItemRequestDto dto) {
        Item existing = requireItem(id);
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setPrice(dto.getPrice());
        existing.setCurrency(dto.getCurrency().toUpperCase());
        existing.setUpdatedAt(Instant.now());
        itemRepository.save(existing);
        Inventory inventory = inventoryRepository.findById(id)
                .orElseGet(() -> new Inventory(id, dto.getStock(), 0L));
        inventoryRepository.save(new Inventory(id, dto.getStock(), inventory.getVersion() + 1));
        return toResponse(existing, dto.getStock());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true)
    })
    public void deleteItem(String id) {
        if (!itemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        itemRepository.deleteById(id);
        inventoryRepository.deleteById(id);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true)
    })
    public ItemResponseDto increaseStock(String id, int quantity) {
        requirePositive(quantity);
        requireItem(id);
        if (inventoryRepository.release(id, quantity) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found");
        }
        return getUncached(id);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true)
    })
    public ItemResponseDto decreaseStock(String id, int quantity) {
        requirePositive(quantity);
        requireItem(id);
        if (inventoryRepository.reserve(id, quantity) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
        }
        return getUncached(id);
    }

    private ItemResponseDto getUncached(String id) {
        return toResponse(requireItem(id), requireInventory(id).getAvailableQuantity());
    }

    private Item requireItem(String id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private Inventory requireInventory(String id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory not found"));
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
    }

    private ItemResponseDto toResponse(Item item, int stock) {
        return new ItemResponseDto(item.getId(), item.getName(), item.getDescription(),
                item.getPrice(), item.getCurrency(), stock);
    }
}
