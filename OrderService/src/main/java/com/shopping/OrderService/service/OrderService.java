package com.shopping.OrderService.service;

import com.shopping.OrderService.entity.Order;
import com.shopping.OrderService.payload.OrderRequest;
import com.shopping.OrderService.payload.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderRequest order);
    OrderResponse cancelOrder(Long orderId);
    void requestPayment(Long orderId);
    List<OrderResponse> getOrders(Long userId);
}