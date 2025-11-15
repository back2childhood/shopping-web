package com.shopping.OrderService.client;

import com.shopping.OrderService.client.dto.ItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "item-service",
        url = "http://localhost:8083"  // your ItemService port
)
public interface ItemClient {

    @GetMapping("/api/items/{id}")
    ItemDTO getItem(@PathVariable("id") String id);
}