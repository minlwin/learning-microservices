package com.jdc.auth.event.publisher;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jdc.auth.model.entity.OutboxEvent;
import com.jdc.auth.model.repo.OutboxEventRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepo repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "#{@kafkaProps.outbox.fixedDelay}")
    public void publishPendingEvents() {
        var pendingEvents = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (var event : pendingEvents) {
            publish(event);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
            markPublished(event);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(event, exception);
        } catch (Exception exception) {
            recordFailure(event, exception);
        }
    }

    private void markPublished(OutboxEvent event) {
        var storedEvent = repository.findById(event.getId()).orElseThrow();
        storedEvent.setPublishedAt(Instant.now());
        storedEvent.setLastError(null);
        repository.save(storedEvent);
    }

    private void recordFailure(OutboxEvent event, Exception exception) {
        var storedEvent = repository.findById(event.getId()).orElseThrow();
        storedEvent.setAttemptCount(storedEvent.getAttemptCount() + 1);
        storedEvent.setLastError(errorMessage(exception));
        repository.save(storedEvent);
        log.warn("Could not publish outbox event {}. It will be retried.", event.getId(), exception);
    }

    private String errorMessage(Exception exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }
}
