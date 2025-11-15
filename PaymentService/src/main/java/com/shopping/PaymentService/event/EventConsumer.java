package com.shopping.PaymentService.event;//package com.shopping.OrderService.event;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class EventConsumer {
    @Autowired
    private EventProducer producer;

    @KafkaListener(topics = "event", groupId = "payment-service-group")
    public void createOrder(String message) {
        if (message == null) {
            return;
        }

        Event event = JSONObject.parseObject(message, Event.class);

        // spend money logic code

        Event result = new Event()
                .setTopic("payment-result")
                .setOrderId(event.getOrderId())
                .setUserId(event.getUserId())
                .setItems(event.getItems().stream().map(i -> new Event.ItemQuantity(i.getItemId(), i.getPrice(), i.getQuantity())).collect(Collectors.toList()).stream().collect(Collectors.toList()));
        producer.sendMessage(result);

    }

}