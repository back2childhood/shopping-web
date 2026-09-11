package com.shopping.ItemService.service;

import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;

import java.util.List;

public interface ItemService {
    ItemResponseDto createItem(ItemRequestDto item, Long userId);
    ItemResponseDto getItemById(String id);
    List<ItemResponseDto> getAllItems();
    List<ItemResponseDto> getItemsByUserId(Long userId);
    ItemResponseDto updateItem(String id, ItemRequestDto item, Long userId);
    void deleteItem(String id, Long userId);
    ItemResponseDto increaseStock(String id, int quantity);
    ItemResponseDto decreaseStock(String id, int quantity);
}
