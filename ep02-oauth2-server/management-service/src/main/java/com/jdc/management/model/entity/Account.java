package com.jdc.management.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(nullable = false)
	private String name;
	
	@Column(nullable = false)
	private String phone;
	
	@Column(nullable = false, unique = true)
	private String email;
	
	@Column(nullable = false)
	private Status status;
	
	@Column(nullable = false)
	private Instant registeredAt;

	private Instant activatedAt;
	
	public enum Status {
		Pending, Activated
	}
}
