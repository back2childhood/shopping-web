package com.shopping.OrderService.service;

import com.shopping.OrderService.client.ItemClient;
import com.shopping.OrderService.client.dto.ItemDTO;
import com.shopping.OrderService.payload.OrderRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderItemLookupServiceTest {
    private final ThreadPoolTaskExecutor executor = createExecutor();

    @AfterEach
    void shutDownExecutor() {
        executor.shutdown();
    }

    @Test
    void loadsIndependentItemsConcurrentlyAndKeepsRequestOrder() {
        CountDownLatch bothCallsStarted = new CountDownLatch(2);
        CountDownLatch releaseCalls = new CountDownLatch(1);
        ItemClient itemClient = new ItemClient() {
            @Override
            public ItemDTO getItem(String id) {
                try {
                    return awaitOtherLookupAndReturn(bothCallsStarted, releaseCalls, item(id));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while loading item", exception);
                }
            }

            @Override
            public ItemDTO reserve(String id, java.util.Map<String, Integer> quantity) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ItemDTO release(String id, java.util.Map<String, Integer> quantity) {
                throw new UnsupportedOperationException();
            }
        };

        OrderItemLookupService service = new OrderItemLookupService(itemClient, executor);
        List<OrderRequest.OrderItemRequest> requests = List.of(request("item-1"), request("item-2"));

        List<ItemDTO> result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Thread releaser = new Thread(() -> {
                try {
                    assertTrue(bothCallsStarted.await(1, TimeUnit.SECONDS));
                    releaseCalls.countDown();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            releaser.start();
            return service.getItems(requests);
        });

        assertEquals(List.of("item-1", "item-2"), result.stream().map(ItemDTO::getId).toList());
    }

    private ItemDTO awaitOtherLookupAndReturn(
            CountDownLatch bothCallsStarted,
            CountDownLatch releaseCalls,
            ItemDTO item) throws InterruptedException {
        bothCallsStarted.countDown();
        if (!releaseCalls.await(1, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Item lookups did not run concurrently");
        }
        return item;
    }

    private static ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("order-item-lookup-test-");
        executor.initialize();
        return executor;
    }

    private OrderRequest.OrderItemRequest request(String itemId) {
        OrderRequest.OrderItemRequest request = new OrderRequest.OrderItemRequest();
        request.setItemId(itemId);
        request.setQuantity(1);
        return request;
    }

    private ItemDTO item(String id) {
        ItemDTO item = new ItemDTO();
        item.setId(id);
        item.setName(id);
        item.setPrice(BigDecimal.TEN);
        item.setCurrency("USD");
        item.setStock(10);
        return item;
    }
}
