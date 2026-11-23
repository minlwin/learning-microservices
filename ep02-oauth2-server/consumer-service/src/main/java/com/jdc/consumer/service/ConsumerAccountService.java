package com.jdc.consumer.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.jdc.consumer.api.management.input.ConsumerAccountSearch;
import com.jdc.consumer.api.management.input.ConsumerRegistrationForm;
import com.jdc.consumer.api.management.output.ConsumerAccountListItem;
import com.jdc.consumer.api.mobile.input.SelfRegistrationForm;
import com.jdc.consumer.api.mobile.output.ConsumerProfile;
import com.jdc.consumer.event.ConsumerAccountApproved;
import com.jdc.consumer.event.ConsumerAccountRejected;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.OutboxEvent;
import com.jdc.consumer.model.query.ConsumerAccountQuery;
import com.jdc.consumer.model.repo.AccountRepo;
import com.jdc.consumer.model.repo.OutboxEventRepo;
import com.jdc.consumer.props.KafkaProps;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumerAccountService {
    private final AccountRepo accounts;
    private final OutboxEventRepo outboxEvents;
    private final ConsumerAccountQuery accountQuery;
    private final AuthConsumerProfileCheckService authProfileCheck;
    private final ConsumerAccountRegistrationService registrationService;
    private final KafkaProps kafkaProperties;
    private final ObjectMapper objectMapper;

    /**
     * Date filters represent inclusive UTC calendar dates.
     */
    public Page<ConsumerAccountListItem> search(ConsumerAccountSearch form, int page, int size) {
        PageRequest.of(page, size);

        if (form.createdFrom() != null && form.createdTo() != null && form.createdFrom().isAfter(form.createdTo())) {
            throw new BusinessRuleViolationException("The start date must not be after the end date.");
        }

        return accounts.search(
                builder -> accountQuery.select(builder, form),
                builder -> accountQuery.count(builder, form),
                page, size);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void register(ConsumerRegistrationForm form) {
        if (accounts.existsByEmail(form.email())) {
            throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
        }

        authProfileCheck.requireNoConsumerProfile(form.email());
        registrationService.create(form);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void register(SelfRegistrationForm form) {
        authProfileCheck.requirePendingConsumerProfile(form.email());
        registrationService.create(form);
    }

    public ConsumerProfile findByUsername(String username) {
        var account = accounts.findByEmail(username).orElseThrow(
                () -> new ResourceNotFoundException("No consumer account was found for the authenticated user."));
        return ConsumerProfile.from(account);
    }

    @Transactional
    public void approve(UUID id) {
        var account = accounts.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No consumer account was found for the provided ID."));

        if (account.getStatus() != Account.Status.Applied) {
            throw new BusinessRuleViolationException("Only an applied consumer account can be approved.");
        }

        account.setStatus(Account.Status.Approved);
        account.setApprovedAt(Instant.now());
        recordApprovalEvent(account);
    }

    @Transactional
    public void reject(UUID id) {
        var account = accounts.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No consumer account was found for the provided ID."));
        if (account.getStatus() != Account.Status.Applied) {
            throw new BusinessRuleViolationException("Only an applied consumer account can be rejected.");
        }
        account.setStatus(Account.Status.Rejected);
        account.setRejectedAt(Instant.now());
        recordRejectionEvent(account);
    }

    private void recordApprovalEvent(Account account) {
        var event = new ConsumerAccountApproved(
                UUID.randomUUID(), account.getId(), account.getEmail(), "Consumer", account.getRegistrationType().name());
        var outboxEvent = new OutboxEvent();
        outboxEvent.setId(event.eventId());
        outboxEvent.setTopic(kafkaProperties.getAccount().getApprovedTopic());
        outboxEvent.setAggregateKey(account.getId().toString());
        outboxEvent.setPayload(serialize(event));
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setNextAttemptAt(outboxEvent.getCreatedAt());
        outboxEvent.setAttemptCount(0);
        outboxEvents.save(outboxEvent);
    }

    private void recordRejectionEvent(Account account) {
        var event = new ConsumerAccountRejected(
                UUID.randomUUID(), account.getId(), account.getEmail(), "Consumer", account.getRegistrationType().name());
        var outboxEvent = new OutboxEvent();
        outboxEvent.setId(event.eventId());
        outboxEvent.setTopic(kafkaProperties.getAccount().getRejectedTopic());
        outboxEvent.setAggregateKey(account.getId().toString());
        outboxEvent.setPayload(serialize(event));
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setNextAttemptAt(outboxEvent.getCreatedAt());
        outboxEvent.setAttemptCount(0);
        outboxEvents.save(outboxEvent);
    }

    private String serialize(ConsumerAccountApproved event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the Consumer account approval event.", exception);
        }
    }

    private String serialize(ConsumerAccountRejected event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the Consumer account rejection event.", exception);
        }
    }
}
