package com.jdc.auth.event.publisher;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jdc.auth.event.service.OutboxClaimService;

import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxClaimService claims;

    @Scheduled(fixedDelayString = "#{@kafkaProps.outbox.fixedDelay}")
    public void publishPendingEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            var pending = claims.claimNext();
            if (pending.isEmpty()) {
                return;
            }
            var event = pending.get();
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                claims.markPublished(event.getId(), event.getClaimToken());
            } catch (InterruptedException exception) {
                claims.recordFailure(event.getId(), event.getClaimToken(), exception);
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                claims.recordFailure(event.getId(), event.getClaimToken(), exception);
            }
        }
    }
}
