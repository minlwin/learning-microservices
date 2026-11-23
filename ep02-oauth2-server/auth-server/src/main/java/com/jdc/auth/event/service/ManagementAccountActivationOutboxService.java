package com.jdc.auth.event.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.event.ManagementAccountActivated;
import com.jdc.auth.model.entity.OutboxEvent;
import com.jdc.auth.model.repo.OutboxEventRepo;
import com.jdc.auth.props.KafkaProps;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class ManagementAccountActivationOutboxService {

    private final OutboxEventRepo events;
    private final ObjectMapper objectMapper;
    private final KafkaProps kafkaProps;

    public void enqueue(UUID accountId, String email, Instant activatedAt) {
        var event = new ManagementAccountActivated(1, UUID.randomUUID(), accountId, email, activatedAt);
        var outboxEvent = new OutboxEvent();
        outboxEvent.setId(event.eventId());
        outboxEvent.setTopic(kafkaProps.getManagementAccount().getActivatedTopic());
        outboxEvent.setAggregateKey(accountId.toString());
        outboxEvent.setPayload(toJson(event));
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setNextAttemptAt(outboxEvent.getCreatedAt());
        outboxEvent.setAttemptCount(0);
        events.save(outboxEvent);
    }

    private String toJson(ManagementAccountActivated event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize the management account activation event.", exception);
        }
    }
}
