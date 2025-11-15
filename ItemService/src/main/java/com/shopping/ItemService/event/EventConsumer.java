package com.shopping.ItemService.event;//package com.shopping.OrderService.event;

import com.alibaba.fastjson2.JSONObject;
import com.shopping.ItemService.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

    private final ItemService itemService;

    @Autowired
    public EventConsumer(ItemService itemService) {
        this.itemService = itemService;
    }

    @KafkaListener(topics = "order-created", groupId = "item-service-group")
    public void createOrder(String message) {
        if (message == null) {
            return;
        }

        Event event = JSONObject.parseObject(message, Event.class);

        for(Event.ItemQuantity item : event.getItems()){
            itemService.decreaseStock(item.getItemId(), item.getQuantity());
        }
    }

    @KafkaListener(topics = "order-cancelled", groupId = "item-service-group")
    public void cancelOrder(String message) {
        if (message == null) {
            return;
        }

        Event event = JSONObject.parseObject(message, Event.class);

        for(Event.ItemQuantity item : event.getItems()){
            itemService.increaseStock(item.getItemId(), item.getQuantity());
        }
    }
}