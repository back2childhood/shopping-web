package com.shopping.OrderService.dao;

import com.shopping.OrderService.entity.Order;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;


public interface OrderRepository extends CassandraRepository<Order, Long> {
    List<Order> findAllByUserId(Long userId);
}