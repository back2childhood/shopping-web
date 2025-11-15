package com.shopping.OrderService.controller;

import com.shopping.OrderService.entity.Order;
import com.shopping.OrderService.payload.OrderRequest;
import com.shopping.OrderService.payload.OrderResponse;
import com.shopping.OrderService.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    @Autowired
    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest order) {
        return ResponseEntity.ok(service.createOrder(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(service.cancelOrder(orderId));
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<String> payOrder(@PathVariable Long orderId) {
        service.requestPayment(orderId);
        return ResponseEntity.ok("Payment requested");
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrders(@PathVariable Long userId) {
        List<OrderResponse> res = service.getOrders(userId);
        return ResponseEntity.ok(res);
    }
}

