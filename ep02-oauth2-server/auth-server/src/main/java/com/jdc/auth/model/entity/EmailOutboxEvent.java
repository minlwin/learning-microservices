package com.jdc.auth.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "email_outbox_event")
public class EmailOutboxEvent {
	@Id
	private UUID id;

	@Column(nullable = false)
	private String recipient;

	@Column(name = "invitation_url", nullable = false, length = 2048)
	private String invitationUrl;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;
}
