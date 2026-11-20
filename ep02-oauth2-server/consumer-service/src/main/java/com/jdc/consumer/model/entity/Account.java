package com.jdc.consumer.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Account {

	public enum RegistrationType {
		Self, Management
	}
	
	public enum Status {
		Applied, Approved
	}
	
	@Id
	private String email;
	
	@Column(nullable = false)
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
	private LocalDateTime registeredAt;

	private LocalDateTime approvedAt;
	
}
