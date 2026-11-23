package com.jdc.auth.event.publisher;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.model.entity.EmailOutboxEvent;
import com.jdc.auth.model.repo.EmailOutboxEventRepo;
import com.jdc.auth.service.OtpCodeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxPublisher {
	private final EmailOutboxEventRepo events;
	private final OtpCodeService mailer;

	@Scheduled(fixedDelayString = "#{@kafkaProps.outbox.fixedDelay}")
	public void sendPendingInvitations() {
		for (var event : events.findTop100BySentAtIsNullOrderByCreatedAtAsc()) {
			send(event);
		}
	}

	private void send(EmailOutboxEvent event) {
		try {
			mailer.sendRegistrationInvitation(event.getRecipient(), event.getInvitationUrl());
			markSent(event.getId());
		} catch (Exception exception) {
			recordFailure(event, exception);
		}
	}

	@Transactional
	protected void markSent(java.util.UUID id) {
		var event = events.findById(id).orElseThrow();
		event.setSentAt(Instant.now());
		event.setLastError(null);
		events.save(event);
	}

	@Transactional
	protected void recordFailure(EmailOutboxEvent event, Exception exception) {
		var stored = events.findById(event.getId()).orElseThrow();
		stored.setAttemptCount(stored.getAttemptCount() + 1);
		var message = exception.getMessage();
		if (message == null || message.isBlank()) {
			message = exception.getClass().getSimpleName();
		}
		stored.setLastError(message.length() > 4000 ? message.substring(0, 4000) : message);
		events.save(stored);
		log.warn("Could not send Consumer invitation {}. It will be retried.", event.getId(), exception);
	}
}
