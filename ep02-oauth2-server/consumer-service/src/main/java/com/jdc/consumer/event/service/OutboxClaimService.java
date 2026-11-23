package com.jdc.consumer.event.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.model.entity.OutboxEvent;
import com.jdc.consumer.model.repo.OutboxEventRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration CLAIM_DURATION = Duration.ofMinutes(1);

    private final OutboxEventRepo events;

    @Transactional
    public Optional<OutboxEvent> claimNext() {
        var now = Instant.now();
        var available = events.lockAvailable(now, PageRequest.of(0, 1));
        if (available.isEmpty()) {
            return Optional.empty();
        }
        var event = available.getFirst();
        event.setClaimToken(UUID.randomUUID());
        event.setClaimedUntil(now.plus(CLAIM_DURATION));
        return Optional.of(events.save(event));
    }

    @Transactional
    public void markPublished(UUID id, UUID claimToken) {
        var event = events.findForUpdate(id).orElseThrow();
        if (!claimToken.equals(event.getClaimToken())) {
            return;
        }
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
        event.setClaimToken(null);
        event.setClaimedUntil(null);
    }

    @Transactional
    public void recordFailure(UUID id, UUID claimToken, Exception exception) {
        var event = events.findForUpdate(id).orElseThrow();
        if (!claimToken.equals(event.getClaimToken())) {
            return;
        }
        var now = Instant.now();
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastError(errorMessage(exception));
        event.setClaimToken(null);
        event.setClaimedUntil(null);
        if (event.getAttemptCount() >= MAX_ATTEMPTS) {
            event.setDeadLetteredAt(now);
            log.error("Outbox event {} was dead-lettered after {} attempts.", id, event.getAttemptCount(), exception);
            return;
        }
        event.setNextAttemptAt(now.plusSeconds(retryDelaySeconds(event.getAttemptCount())));
        log.warn("Could not publish outbox event {}. It will be retried after {} seconds.", id,
                retryDelaySeconds(event.getAttemptCount()), exception);
    }

    private long retryDelaySeconds(int attemptCount) {
        return Math.min(30L << (attemptCount - 1), 3600L);
    }

    private String errorMessage(Exception exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }
}
