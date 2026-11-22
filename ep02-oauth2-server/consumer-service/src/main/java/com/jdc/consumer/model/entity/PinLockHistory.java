package com.jdc.consumer.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Data
@Entity
public class PinLockHistory {

	public enum LockType {
		Temporary, Permenant
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private Account account;
	
	@Column(nullable = false)
	private LockType lockType;
	
	@Column(nullable = false)
	private Instant lockAt;
}
