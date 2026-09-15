package com.shopping.ItemService.event;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class EventProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> sendMessage(Event event) {
        return kafkaTemplate.send(event.getTopic(), event.getAggregateId(), JSONObject.toJSONString(event));
    }
}
