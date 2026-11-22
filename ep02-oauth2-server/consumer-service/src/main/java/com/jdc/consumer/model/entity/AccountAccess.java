package com.jdc.consumer.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Data
@Entity
public class AccountAccess {

	public enum Status {
		Login, Logout
	}
	
	@Id
	private UUID id;
	
	@MapsId
	@OneToOne(optional = false)
	private Account account;
	
	@Column(nullable = false)
	private Status status;
	
	@Column(nullable = false)
	private Instant statusChangeAt;
}
