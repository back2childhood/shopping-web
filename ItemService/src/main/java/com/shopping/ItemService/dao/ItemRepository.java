package com.shopping.ItemService.dao;

import com.shopping.ItemService.entity.Item;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends CassandraRepository<Item, String> {
}
