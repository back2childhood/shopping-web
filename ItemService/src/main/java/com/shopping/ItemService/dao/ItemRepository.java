package com.shopping.ItemService.dao;

import com.shopping.ItemService.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, String> {
    List<Item> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
