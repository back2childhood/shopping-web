package com.shopping.OrderService.service.impl;

import com.shopping.OrderService.client.ItemClient;
import com.shopping.OrderService.client.dto.ItemDTO;
import com.shopping.OrderService.dao.OrderRepository;
import com.shopping.OrderService.entity.Order;
import com.shopping.OrderService.entity.OrderItem;
import com.shopping.OrderService.payload.OrderRequest;
import com.shopping.OrderService.payload.OrderResponse;
import com.shopping.OrderService.payload.OrderStatus;
import com.shopping.OrderService.service.OrderService;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository repository;
    private final ItemClient itemClient;

    public OrderServiceImpl(OrderRepository repository, ItemClient itemClient) {
        this.repository = repository;
        this.itemClient = itemClient;
    }

    @Override
    @Transactional
    @CachePut(value = "orders", key = "#result.id")
    public OrderResponse createOrder(OrderRequest request) {
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());

        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        List<OrderRequest.OrderItemRequest> reserved = new ArrayList<>();

        try {
            for (OrderRequest.OrderItemRequest requested : request.getItems()) {
                ItemDTO item = itemClient.getItem(requested.getItemId());
                if (currency == null) {
                    currency = item.getCurrency();
                } else if (!currency.equals(item.getCurrency())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "All items in an order must use the same currency");
                }

                itemClient.reserve(requested.getItemId(), Map.of("quantity", requested.getQuantity()));
                reserved.add(requested);

                OrderItem line = new OrderItem();
                line.setItemId(item.getId());
                line.setItemName(item.getName());
                line.setPrice(item.getPrice());
                line.setQuantity(requested.getQuantity());
                order.addItem(line);
                total = total.add(item.getPrice().multiply(BigDecimal.valueOf(requested.getQuantity())));
            }

            order.setCurrency(currency);
            order.setTotalPrice(total);
            return toResponse(repository.save(order));
        } catch (RuntimeException exception) {
            reserved.forEach(item -> {
                try {
                    itemClient.release(item.getItemId(), Map.of("quantity", item.getQuantity()));
                } catch (RuntimeException ignored) {
                    // A production system would persist this compensation for retry.
                }
            });
            throw exception;
        }
    }

    @Override
    @Cacheable(value = "orders", key = "#orderId")
    public OrderResponse getOrder(Long orderId) {
        return repository.findById(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Override
    public List<OrderResponse> getOrders(Long userId) {
        return repository.findAllByUserId(userId).stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setTotalPrice(order.getTotalPrice());
        response.setCurrency(order.getCurrency());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(order.getItems().stream().map(this::toItemResponse).toList());
        return response;
    }

    private OrderResponse.OrderItemResponse toItemResponse(OrderItem item) {
        OrderResponse.OrderItemResponse response = new OrderResponse.OrderItemResponse();
        response.setItemId(item.getItemId());
        response.setItemName(item.getItemName());
        response.setQuantity(item.getQuantity());
        response.setPrice(item.getPrice());
        return response;
    }
}
