package com.jdc.management.event.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.event.PinLockSettingChangedEvent;
import com.jdc.management.model.entity.OutboxEvent;
import com.jdc.management.model.repo.OutboxEventRepo;
import com.jdc.management.props.KafkaProps;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class PinLockSettingOutboxService {

    private final OutboxEventRepo repository;
    private final ObjectMapper objectMapper;
    private final KafkaProps kafkaProps;

    public void enqueue(PinLockSettingChangedEvent event) {
        var outboxEvent = new OutboxEvent();
        outboxEvent.setId(event.eventId());
        outboxEvent.setTopic(kafkaProps.getPinLock().getTopic());
        outboxEvent.setAggregateKey(event.system());
        outboxEvent.setPayload(toJson(event));
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setAttemptCount(0);
        repository.save(outboxEvent);
    }

    private String toJson(PinLockSettingChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize the PIN lock setting event.", exception);
        }
    }
}
