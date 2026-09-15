package com.shopping.OrderService.service;

import com.shopping.OrderService.client.ItemClient;
import com.shopping.OrderService.client.dto.ItemDTO;
import com.shopping.OrderService.payload.OrderRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
public class OrderItemLookupService {
    private final ItemClient itemClient;
    private final Executor itemLookupExecutor;

    public OrderItemLookupService(
            ItemClient itemClient,
            @Qualifier("orderItemLookupExecutor") Executor itemLookupExecutor) {
        this.itemClient = itemClient;
        this.itemLookupExecutor = itemLookupExecutor;
    }

    public List<ItemDTO> getItems(List<OrderRequest.OrderItemRequest> requestedItems) {
        List<CompletableFuture<ItemDTO>> lookups = requestedItems.stream()
                .map(requested -> CompletableFuture.supplyAsync(
                        () -> itemClient.getItem(requested.getItemId()), itemLookupExecutor))
                .toList();

        try {
            CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).join();
            return lookups.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException exception) {
            lookups.forEach(lookup -> lookup.cancel(true));
            throw propagate(exception);
        }
    }

    private RuntimeException propagate(CompletionException exception) {
        Throwable cause = exception;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Unable to load item details", cause);
    }
}
