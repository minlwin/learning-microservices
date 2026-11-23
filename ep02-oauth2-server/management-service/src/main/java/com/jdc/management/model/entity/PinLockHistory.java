package com.jdc.management.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_management_pin_lock_history_event", columnNames = "event_id"))
public class PinLockHistory {

	public enum LockType {
		Temporary, Permenant, Released
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "event_id", nullable = false)
	private UUID eventId;
	
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private Account account;
	
	@Column(nullable = false)
	private LockType lockType;
	
	@Column(nullable = false)
	private Instant lockAt;

	private Instant lockedUntil;

	@Column(name = "requested_by")
	private String requestedBy;
}
