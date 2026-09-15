package com.shopping.ItemService.event;

import com.alibaba.fastjson2.JSONObject;
import com.shopping.ItemService.entity.OutboxEvent;
import com.shopping.ItemService.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxService outboxService;
    private final EventProducer eventProducer;

    public OutboxPublisher(OutboxService outboxService, EventProducer eventProducer) {
        this.outboxService = outboxService;
        this.eventProducer = eventProducer;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:1000}")
    public void publishPendingEvents() {
        for (OutboxEvent outboxEvent : outboxService.findPendingBatch()) {
            try {
                Event event = JSONObject.parseObject(outboxEvent.getPayload(), Event.class);
                eventProducer.sendMessage(event).get(10, TimeUnit.SECONDS);
                outboxService.markPublished(outboxEvent.getEventId());
            } catch (Exception exception) {
                outboxService.markFailed(outboxEvent.getEventId(), exception.getMessage());
                log.warn("Unable to publish outbox event {}. It will be retried.",
                        outboxEvent.getEventId(), exception);
            }
        }
    }
}
