package com.jdc.consumer.event.listener;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.event.PinLockActivityChangedEvent;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.entity.PinLockHistory;
import com.jdc.consumer.model.repo.AccountRepo;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
import com.jdc.consumer.model.repo.PinLockHistoryRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PinLockActivityEventListener {

    private final ObjectMapper objectMapper;
    private final AccountRepo accounts;
    private final AccountLockStatusRepo lockStatuses;
    private final PinLockHistoryRepo histories;

    @KafkaListener(topics = "#{@kafkaProps.pinLock.activityTopic}",
            groupId = "#{@kafkaProps.pinLock.activityConsumerGroupId}")
    @Transactional
    public void onPinLockActivity(String payload) {
        var event = deserialize(payload);
        validate(event);
        if (!"Consumer".equals(event.system())) {
            return;
        }
        if (histories.existsByEventId(event.eventId())) {
            return;
        }

        var account = accounts.findByEmail(event.username())
                .orElseThrow(() -> new ResourceNotFoundException("Consumer account for PIN lock event was not found."));
        var history = new PinLockHistory();
        history.setId(UUID.randomUUID());
        history.setEventId(event.eventId());
        history.setAccount(account);
        history.setLockType(lockType(event.action()));
        history.setLockAt(event.changedAt() == null ? Instant.now() : event.changedAt());
        history.setLockedUntil(event.lockedUntil());
        history.setRequestedBy(event.requestedBy());
        histories.save(history);
        updatePermanentLockProjection(account, event);
        log.info("Recorded PIN lock event {} for consumer account {}.", event.eventId(), account.getId());
    }

    private void updatePermanentLockProjection(Account account, PinLockActivityChangedEvent event) {
        AccountLockStatus.Status status = switch (event.action()) {
            case "PermanentlyLocked" -> AccountLockStatus.Status.Locked;
            case "PermanentUnlocked" -> AccountLockStatus.Status.Active;
            default -> null;
        };
        if (status == null) {
            return;
        }

        var lockStatus = lockStatuses.findForUpdate(account.getId()).orElseGet(AccountLockStatus::new);
        lockStatus.setId(account.getId());
        lockStatus.setAccount(account);
        lockStatus.setStatus(status);
        lockStatus.setStatusChangeAt(event.changedAt() == null ? Instant.now() : event.changedAt());
        lockStatuses.save(lockStatus);
    }

    private PinLockHistory.LockType lockType(String action) {
        return switch (action) {
            case "TemporarilyLocked" -> PinLockHistory.LockType.Temporary;
            case "PermanentlyLocked" -> PinLockHistory.LockType.Permenant;
            case "PermanentUnlocked" -> PinLockHistory.LockType.Released;
            default -> throw new IllegalArgumentException("Unsupported PIN lock activity action.");
        };
    }

    private void validate(PinLockActivityChangedEvent event) {
        if (event.schemaVersion() != 1 || event.eventId() == null || event.profileId() == null
                || event.username() == null || event.username().isBlank() || event.system() == null
                || event.system().isBlank() || event.action() == null || event.action().isBlank()) {
            throw new IllegalArgumentException("PIN lock activity event is invalid.");
        }
    }

    private PinLockActivityChangedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PinLockActivityChangedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not deserialize the PIN lock activity event.", exception);
        }
    }
}
