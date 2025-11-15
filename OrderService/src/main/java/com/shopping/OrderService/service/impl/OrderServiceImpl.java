package com.shopping.OrderService.service.impl;

import com.shopping.OrderService.client.ItemClient;
import com.shopping.OrderService.client.dto.ItemDTO;
import com.shopping.OrderService.dao.OrderRepository;
import com.shopping.OrderService.entity.Order;
import com.shopping.OrderService.entity.OrderItem;
import com.shopping.OrderService.event.Event;
import com.shopping.OrderService.event.EventProducer;
import com.shopping.OrderService.payload.OrderRequest;
import com.shopping.OrderService.payload.OrderResponse;
import com.shopping.OrderService.payload.OrderStatus;
import com.shopping.OrderService.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository repo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventProducer eventProducer;
    private final ItemClient itemClient;

    @Autowired
    public OrderServiceImpl(OrderRepository repo, KafkaTemplate<String, Object> kafkaTemplate, EventProducer eventProducer, ItemClient itemClient) {
        this.repo = repo;
        this.kafkaTemplate = kafkaTemplate;
        this.eventProducer = eventProducer;
        this.itemClient = itemClient;
    }

    @Override
    public OrderResponse createOrder(OrderRequest request) {

        Order order = orderRequestToOrder(request);

        // Kafka: decrease stock
        Event updateItemEvent = new Event()
                .setTopic("order-created")
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setItems(order.getItems().stream().map(i -> new Event.ItemQuantity(i.getItemId(), i.getPrice(), i.getQuantity())).collect(Collectors.toList()).stream().collect(Collectors.toList()));
        eventProducer.sendMessage(updateItemEvent);
        System.out.println(updateItemEvent);
        log.info("Sent order-created event: {}", updateItemEvent);

        repo.save(order);

        return orderToResponse(order);
    }

    @Override
    public OrderResponse cancelOrder(Long orderId) {
        Order order = repo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(OrderStatus.CANCELLED);
        repo.save(order);

        Event updateItemEvent = new Event()
                .setTopic("order-cancelled")
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setItems(order.getItems().stream().map(i -> new Event.ItemQuantity(i.getItemId(), i.getPrice(), i.getQuantity())).collect(Collectors.toList()).stream().collect(Collectors.toList()));
        eventProducer.sendMessage(updateItemEvent);
        log.info("Sent order-created event: {}", updateItemEvent);
//        log.info("Sent order-cancelled event: {}", event);
        return orderToResponse(order);
    }

    @Override
    public void requestPayment(Long orderId) {
        Order order = repo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Kafka: payment request
        Event payEvent = new Event()
                .setTopic("payment-request")
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setItems(order.getItems().stream().map(i -> new Event.ItemQuantity(i.getItemId(), i.getPrice(), i.getQuantity())).collect(Collectors.toList()).stream().collect(Collectors.toList()));
        eventProducer.sendMessage(payEvent);
        log.info("Sent payment-request event: {}", payEvent);
    }

    @Override
    public List<OrderResponse> getOrders(Long userId){
        List<Order> orders = repo.findAllByUserId(userId);
        List<OrderResponse> orderResponses = new ArrayList<>();
        for(Order order : orders){
            orderResponses.add(orderToResponse(order));
        }
        return orderResponses;
    }

    public Order orderRequestToOrder(OrderRequest request) {

        List<OrderItem> orderItems = new ArrayList<>();
        double totalPrice = 0.0;

        for (OrderRequest.OrderItemRequest reqItem : request.getItems()) {

            // Fetch item details from ItemService
            ItemDTO item = itemClient.getItem(reqItem.getItemId());
            if (item == null) {
                throw new RuntimeException("Item not found: " + reqItem.getItemId());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setItemId(reqItem.getItemId());
            orderItem.setQuantity(reqItem.getQuantity());
            orderItem.setPrice(item.getPrice());     // <-- REAL price
//            orderItem.setItemName(item.getName());   // <-- REAL name

            orderItems.add(orderItem);

            totalPrice += item.getPrice() * reqItem.getQuantity();
        }

        Order order = new Order();
        order.setId(ThreadLocalRandom.current().nextLong(1_000_000_000L));
        order.setUserId(request.getUserId());
        order.setItems(orderItems);
        order.setTotalPrice(totalPrice);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());

        return order;
    }

    private OrderResponse orderToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setTotalPrice(order.getTotalPrice());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());

        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        response.setItems(itemResponses);
        return response;
    }

    private OrderResponse.OrderItemResponse toItemResponse(OrderItem item) {
        OrderResponse.OrderItemResponse res = new OrderResponse.OrderItemResponse();
        res.setItemId(item.getItemId());
        res.setQuantity(item.getQuantity());
        res.setPrice(item.getPrice());
//        res.setItemName(item.getItemName());
        return res;
    }
}
