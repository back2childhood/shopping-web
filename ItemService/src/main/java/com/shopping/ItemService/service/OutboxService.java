package com.shopping.ItemService.service;

import com.alibaba.fastjson2.JSONObject;
import com.shopping.ItemService.dao.OutboxEventRepository;
import com.shopping.ItemService.entity.OutboxEvent;
import com.shopping.ItemService.entity.OutboxStatus;
import com.shopping.ItemService.event.Event;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Event event) {
        outboxEventRepository.save(OutboxEvent.builder()
                .eventId(event.getEventId())
                .aggregateId(event.getAggregateId())
                .aggregateType("ITEM")
                .eventType(event.getEventType())
                .aggregateVersion(event.getAggregateVersion())
                .payload(JSONObject.toJSONString(event))
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .createdAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findPendingBatch() {
        return outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(outboxEvent -> {
            outboxEvent.setStatus(OutboxStatus.PUBLISHED);
            outboxEvent.setPublishedAt(Instant.now());
            outboxEvent.setLastError(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String error) {
        outboxEventRepository.findById(eventId).ifPresent(outboxEvent -> {
            outboxEvent.setAttemptCount(outboxEvent.getAttemptCount() + 1);
            outboxEvent.setLastError(truncate(error));
        });
    }

    private String truncate(String error) {
        if (error == null) {
            return "Unknown Kafka publish error";
        }
        return error.length() <= 2000 ? error : error.substring(0, 2000);
    }
}
