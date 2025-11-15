package com.shopping.ItemService.service.impl;

import com.shopping.Common.exception.ResourceNotFoundException;
import com.shopping.ItemService.dao.ItemRepository;
import com.shopping.ItemService.entity.Item;
import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;
import com.shopping.ItemService.service.ItemService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    private ItemRepository itemRepository;

    private ModelMapper modelMapper;

    @Autowired
    public ItemServiceImpl(ItemRepository itemRepository, ModelMapper modelMapper) {
        this.itemRepository = itemRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    @CachePut(value = "items", key = "#result.id")
    @CacheEvict(value = "itemList", allEntries = true)
    public ItemResponseDto createItem(ItemRequestDto dto) {
        Item item = modelMapper.map(dto, Item.class);
        Item savedItem = itemRepository.save(item);
        return modelMapper.map(savedItem, ItemResponseDto.class);
    }

    @Override
    @Cacheable(value = "items", key = "#id")
    public ItemResponseDto getItemById(String id) {
        return itemRepository.findById(id)
                .map(item -> modelMapper.map(item, ItemResponseDto.class))
                .orElseThrow(() -> new ResourceNotFoundException("Item", "id", String.valueOf(id)));
    }

    @Override
    @Cacheable(value = "itemList")
    public List<ItemResponseDto> getAllItems() {
        return itemRepository.findAll().stream()
                .map(item -> modelMapper.map(item, ItemResponseDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @CachePut(value = "items", key = "#result.id")
    public ItemResponseDto updateItem(String id, ItemRequestDto updatedItem) {
        Item item = modelMapper.map(updatedItem, Item.class);
        Item savedItem = itemRepository.save(item);
        return modelMapper.map(savedItem, ItemResponseDto.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true)
    })
    public void deleteItem(String id) {
        itemRepository.deleteById(id);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true)
    })
    public ItemResponseDto increaseStock(String id, int quantity) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", "id", id));
        item.setStock(item.getStock() + quantity);
        Item savedItem = itemRepository.save(item);
        return modelMapper.map(savedItem, ItemResponseDto.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "items", key = "#id"),
            @CacheEvict(value = "itemList", allEntries = true)
    })
    public ItemResponseDto decreaseStock(String id, int quantity) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", "id", id));
        if (item.getStock() < quantity) {
            throw new RuntimeException("Insufficient stock");
        }
        item.setStock(item.getStock() - quantity);
        Item savedItem = itemRepository.save(item);
        return modelMapper.map(savedItem, ItemResponseDto.class);
    }
}