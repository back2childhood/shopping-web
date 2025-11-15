package com.shopping.ItemService.service;

import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;

import java.util.List;

public interface ItemService {
    ItemResponseDto createItem(ItemRequestDto item);
    ItemResponseDto getItemById(String id);
    List<ItemResponseDto> getAllItems();
    ItemResponseDto updateItem(String id, ItemRequestDto item);
    void deleteItem(String id);
    ItemResponseDto increaseStock(String id, int quantity);
    public ItemResponseDto decreaseStock(String id, int quantity);
}