package com.shopping.ItemService.dao;

import com.shopping.ItemService.entity.ItemProjection;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemProjectionRepository extends CassandraRepository<ItemProjection, String> {
}
