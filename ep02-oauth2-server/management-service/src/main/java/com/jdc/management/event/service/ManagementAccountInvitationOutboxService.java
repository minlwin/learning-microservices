package com.jdc.management.event.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.event.ManagementAccountInvitationRequested;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.entity.OutboxEvent;
import com.jdc.management.model.repo.OutboxEventRepo;
import com.jdc.management.props.KafkaProps;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class ManagementAccountInvitationOutboxService {

    private final OutboxEventRepo events;
    private final ObjectMapper objectMapper;
    private final KafkaProps kafkaProps;

    public void enqueue(Account account) {
        var event = new ManagementAccountInvitationRequested(
                1, UUID.randomUUID(), account.getId(), account.getEmail(), Instant.now());
        var outboxEvent = new OutboxEvent();
        outboxEvent.setId(event.eventId());
        outboxEvent.setTopic(kafkaProps.getManagementAccount().getInvitationTopic());
        outboxEvent.setAggregateKey(account.getId().toString());
        outboxEvent.setPayload(toJson(event));
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setNextAttemptAt(outboxEvent.getCreatedAt());
        outboxEvent.setAttemptCount(0);
        events.save(outboxEvent);
    }

    private String toJson(ManagementAccountInvitationRequested event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize the management account invitation event.", exception);
        }
    }
}
