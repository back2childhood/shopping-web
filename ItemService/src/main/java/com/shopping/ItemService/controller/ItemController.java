package com.shopping.ItemService.controller;

import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;
import com.shopping.ItemService.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ItemResponseDto createItem(@Valid @RequestBody ItemRequestDto item) {
        return itemService.createItem(item);
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
    public ItemResponseDto updateItem(@PathVariable String id, @RequestBody ItemRequestDto item) {
        return itemService.updateItem(id, item);
    }

    @DeleteMapping("/{id}")
    public void deleteItem(@PathVariable String id) {
        itemService.deleteItem(id);
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

}
