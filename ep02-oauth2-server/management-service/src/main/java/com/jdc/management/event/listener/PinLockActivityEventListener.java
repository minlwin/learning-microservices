package com.jdc.management.event.listener;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.event.PinLockActivityChangedEvent;
import com.jdc.management.exceptions.ResourceNotFoundException;
import com.jdc.management.model.entity.PinLockHistory;
import com.jdc.management.model.repo.AccountRepo;
import com.jdc.management.model.repo.PinLockHistoryRepo;

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
    private final PinLockHistoryRepo histories;

    @KafkaListener(topics = "#{@kafkaProps.pinLock.activityTopic}",
            groupId = "#{@kafkaProps.pinLock.activityConsumerGroupId}")
    @Transactional
    public void onPinLockActivity(String payload) {
        var event = deserialize(payload);
        validate(event);
        if (!"Management".equals(event.system())) {
            return;
        }
        if (histories.existsByEventId(event.eventId())) {
            return;
        }

        var account = accounts.findByEmail(event.username())
                .orElseThrow(() -> new ResourceNotFoundException("Management account for PIN lock event was not found."));
        var history = new PinLockHistory();
        history.setId(UUID.randomUUID());
        history.setEventId(event.eventId());
        history.setAccount(account);
        history.setLockType(lockType(event.action()));
        history.setLockAt(event.changedAt() == null ? Instant.now() : event.changedAt());
        history.setLockedUntil(event.lockedUntil());
        history.setRequestedBy(event.requestedBy());
        histories.save(history);
        log.info("Recorded PIN lock event {} for management account {}.", event.eventId(), account.getId());
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
