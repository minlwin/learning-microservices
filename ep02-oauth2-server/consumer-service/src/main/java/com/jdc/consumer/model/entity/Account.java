package com.jdc.consumer.model.entity;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Data
@Entity
public class Account {

	public enum RegistrationType {
		Self, Management
	}

	public enum Status {
		Applied, Approved, Rejected
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	private String name;

	@Column(nullable = false)
	private String phone;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false)
	private LocalDate dateOfBirth;

	@Column(nullable = false)
	private RegistrationType registrationType;

	@Column(nullable = false)
	private Status status;

	@Column(nullable = false)
	private Instant registeredAt;

	private Instant approvedAt;

	private Instant rejectedAt;

	@OneToOne(mappedBy = "account")
	private AccountAccess access;

	@OneToOne(mappedBy = "account")
	private AccountLockStatus lockStatus;

}
