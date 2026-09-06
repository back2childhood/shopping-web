package com.shopping.OrderService.client;

import com.shopping.OrderService.client.dto.ItemDTO;
import com.shopping.OrderService.config.ItemClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(
        name = "ITEM-SERVICE",
        configuration = ItemClientConfig.class
)
public interface ItemClient {

    @GetMapping("/api/items/{id}")
    ItemDTO getItem(@PathVariable("id") String id);

    @PostMapping("/api/items/{id}/decrease-stock")
    ItemDTO reserve(@PathVariable("id") String id, @RequestBody Map<String, Integer> quantity);

    @PostMapping("/api/items/{id}/increase-stock")
    ItemDTO release(@PathVariable("id") String id, @RequestBody Map<String, Integer> quantity);
}
