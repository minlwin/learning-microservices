package com.jdc.auth.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.jdc.auth.model.entity.dto.OtpRecord;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Registration {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(nullable = false)
	private String email;
	
	@Column(nullable = false)
	private Instant registeredAt;
	
	private Instant initializedAt;

	@Column(nullable = false)
	private boolean accountApproved;

	private Instant invitationExpiresAt;
	
	@Embedded
	private OtpRecord otpRecord = new OtpRecord();
}
