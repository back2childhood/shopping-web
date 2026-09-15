package com.shopping.ItemService.controller;

import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;
import com.shopping.ItemService.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private ItemService itemService;

    @Autowired
    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ItemResponseDto createItem(@Valid @RequestBody ItemRequestDto item, Authentication authentication) {
        return itemService.createItem(item, currentUserId(authentication));
    }

    @GetMapping("/mine")
    public List<ItemResponseDto> getMyItems(Authentication authentication) {
        return itemService.getItemsByUserId(currentUserId(authentication));
    }

    @GetMapping("/{id}")
    public ItemResponseDto getItemById(@PathVariable String id) {
        return itemService.getItemById(id);
    }

    @GetMapping
    public List<ItemResponseDto> getAllItems() {
        return itemService.getAllItems();
    }

    @PutMapping("/{id}")
    public ItemResponseDto updateItem(@PathVariable String id, @Valid @RequestBody ItemRequestDto item,
                                      Authentication authentication) {
        return itemService.updateItem(id, item, currentUserId(authentication));
    }

    @DeleteMapping("/{id}")
    public void deleteItem(@PathVariable String id, Authentication authentication) {
        itemService.deleteItem(id, currentUserId(authentication));
    }

    @PostMapping("/{id}/increase-stock")
    public ItemResponseDto increaseStock(@PathVariable String id, @RequestBody Map<String, Integer> request) {
        int quantity = request.get("quantity");
        return itemService.increaseStock(id, quantity);
    }

    @PostMapping("/{id}/decrease-stock")
    public ItemResponseDto decreaseStock(@PathVariable String id, @RequestBody Map<String, Integer> request) {
        int quantity = request.get("quantity");
        return itemService.decreaseStock(id, quantity);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("Authenticated user ID is required");
        }
        return Long.valueOf(authentication.getName());
    }

}
