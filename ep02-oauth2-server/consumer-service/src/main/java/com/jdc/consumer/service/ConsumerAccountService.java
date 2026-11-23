package com.jdc.consumer.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.jdc.consumer.model.entity.AccountAccess;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.entity.OutboxEvent;
import com.jdc.consumer.model.query.ConsumerAccountQuery;
import com.jdc.consumer.model.repo.AccountAccessRepo;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
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
    private final AccountAccessRepo accesses;
    private final AccountLockStatusRepo locks;
    private final OutboxEventRepo outboxEvents;
    private final ConsumerAccountQuery accountQuery;
    private final AuthConsumerProfileCheckService authProfileCheck;
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

    @Transactional
    public void register(ConsumerRegistrationForm form) {
        if (accounts.existsByEmail(form.email())) {
            throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
        }

        authProfileCheck.requireNoConsumerProfile(form.email());
        register(form.name(), form.email(), form.phone(), form.dob(), form.address(),
                Account.RegistrationType.Management);
    }

    @Transactional
    public void register(SelfRegistrationForm form) {
        authProfileCheck.requirePendingConsumerProfile(form.email());
        register(form.name(), form.email(), form.phone(), form.dob(), form.address(), Account.RegistrationType.Self);
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

    private Account register(String name, String email, String phone, LocalDate dob, String address,
            Account.RegistrationType type) {
        if (accounts.existsByEmail(email)) {
            throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
        }
        var now = Instant.now();
        var account = new Account();
        account.setName(name);
        account.setEmail(email);
        account.setPhone(phone);
        account.setDateOfBirth(dob);
        account.setAddress(address);
        account.setRegistrationType(type);
        account.setStatus(Account.Status.Applied);
        account.setRegisteredAt(now);
        account = accounts.save(account);

        var access = new AccountAccess();
        access.setAccount(account);
        access.setStatus(AccountAccess.Status.Logout);
        access.setStatusChangeAt(now);
        accesses.save(access);
        account.setAccess(access);

        var lock = new AccountLockStatus();
        lock.setAccount(account);
        lock.setStatus(AccountLockStatus.Status.Active);
        lock.setStatusChangeAt(now);
        locks.save(lock);
        account.setLockStatus(lock);
        return account;
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
