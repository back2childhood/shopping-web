package com.shopping.OrderService.service.impl;

import com.shopping.OrderService.client.ItemClient;
import com.shopping.OrderService.client.dto.ItemDTO;
import com.shopping.OrderService.dao.OrderRepository;
import com.shopping.OrderService.entity.Order;
import com.shopping.OrderService.payload.OrderRequest;
import com.shopping.OrderService.payload.OrderResponse;
import com.shopping.OrderService.service.OrderItemLookupService;
import com.shopping.OrderService.service.OrderPersistenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceImplTest {

    @Test
    void createsOrderAfterParallelLookupAndReservesStockInRequestOrder() {
        AtomicReference<Order> savedOrder = new AtomicReference<>();
        OrderRepository repository = repository(savedOrder);
        RecordingItemClient itemClient = new RecordingItemClient();
        itemClient.add(item("item-1", "3.50"));
        itemClient.add(item("item-2", "5.00"));
        OrderItemLookupService lookupService = new OrderItemLookupService(itemClient, Runnable::run);
        OrderPersistenceService persistenceService = new OrderPersistenceService(repository);
        OrderRequest request = request(line("item-1", 2), line("item-2", 1));

        OrderServiceImpl service = new OrderServiceImpl(repository, itemClient, lookupService, persistenceService);
        OrderResponse response = service.createOrder(request);

        assertEquals(List.of("item-1:2", "item-2:1"), itemClient.reservations);
        assertEquals(new BigDecimal("12.00"), response.getTotalPrice());
        assertEquals(2, response.getItems().size());
        assertEquals(101L, savedOrder.get().getId());
    }

    @Test
    void releasesPreviouslyReservedStockWhenLaterReservationFails() {
        AtomicReference<Order> savedOrder = new AtomicReference<>();
        OrderRepository repository = repository(savedOrder);
        RecordingItemClient itemClient = new RecordingItemClient();
        itemClient.add(item("item-1", "3.50"));
        itemClient.add(item("item-2", "5.00"));
        itemClient.failReservationFor = "item-2";
        OrderItemLookupService lookupService = new OrderItemLookupService(itemClient, Runnable::run);
        OrderPersistenceService persistenceService = new OrderPersistenceService(repository);
        OrderRequest request = request(line("item-1", 2), line("item-2", 1));

        OrderServiceImpl service = new OrderServiceImpl(repository, itemClient, lookupService, persistenceService);

        assertThrows(IllegalStateException.class, () -> service.createOrder(request));
        assertEquals(List.of("item-1:2"), itemClient.reservations);
        assertEquals(List.of("item-1:2"), itemClient.releases);
        assertNull(savedOrder.get());
    }

    private OrderRequest request(OrderRequest.OrderItemRequest... items) {
        OrderRequest request = new OrderRequest();
        request.setUserId(7L);
        request.setItems(List.of(items));
        return request;
    }

    private OrderRequest.OrderItemRequest line(String itemId, int quantity) {
        OrderRequest.OrderItemRequest request = new OrderRequest.OrderItemRequest();
        request.setItemId(itemId);
        request.setQuantity(quantity);
        return request;
    }

    private ItemDTO item(String id, String price) {
        ItemDTO item = new ItemDTO();
        item.setId(id);
        item.setName(id);
        item.setPrice(new BigDecimal(price));
        item.setCurrency("USD");
        item.setStock(10);
        return item;
    }

    private OrderRepository repository(AtomicReference<Order> savedOrder) {
        return (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(),
                new Class<?>[]{OrderRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("save")) {
                        Order order = (Order) arguments[0];
                        order.setId(101L);
                        savedOrder.set(order);
                        return order;
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderRepositoryFake";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class RecordingItemClient implements ItemClient {
        private final Map<String, ItemDTO> items = new HashMap<>();
        private final List<String> reservations = new ArrayList<>();
        private final List<String> releases = new ArrayList<>();
        private String failReservationFor;

        void add(ItemDTO item) {
            items.put(item.getId(), item);
        }

        @Override
        public ItemDTO getItem(String id) {
            return items.get(id);
        }

        @Override
        public ItemDTO reserve(String id, Map<String, Integer> quantity) {
            if (id.equals(failReservationFor)) {
                throw new IllegalStateException("insufficient stock");
            }
            reservations.add(id + ":" + quantity.get("quantity"));
            return items.get(id);
        }

        @Override
        public ItemDTO release(String id, Map<String, Integer> quantity) {
            releases.add(id + ":" + quantity.get("quantity"));
            return items.get(id);
        }
    }
}
