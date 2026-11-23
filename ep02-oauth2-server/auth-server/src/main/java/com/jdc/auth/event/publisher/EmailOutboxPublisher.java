package com.jdc.auth.event.publisher;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jdc.auth.event.service.EmailOutboxClaimService;
import com.jdc.auth.service.OtpCodeService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailOutboxPublisher {

    private final EmailOutboxClaimService claims;
    private final OtpCodeService mailer;

    @Scheduled(fixedDelayString = "#{@kafkaProps.outbox.fixedDelay}")
    public void sendPendingInvitations() {
        while (!Thread.currentThread().isInterrupted()) {
            var pending = claims.claimNext();
            if (pending.isEmpty()) {
                return;
            }
            var event = pending.get();
            try {
                mailer.sendInvitation(event.getSystem(), event.getRecipient(), event.getInvitationUrl());
                claims.markSent(event.getId(), event.getClaimToken());
            } catch (Exception exception) {
                claims.recordFailure(event.getId(), event.getClaimToken(), exception);
            }
        }
    }
}
