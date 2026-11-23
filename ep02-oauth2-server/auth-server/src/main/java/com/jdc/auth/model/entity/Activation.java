package com.jdc.auth.model.entity;

import java.util.UUID;
import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;

import com.jdc.auth.model.entity.dto.OtpRecord;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Activation {

	@Id
	private UUID id;
	
	@Column(nullable = false)
	private String email;
	
	private Instant initializedAt;

	@Column(name = "invitation_expires_at", nullable = false)
	private Instant invitationExpiresAt;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "otpCode", column = @Column(name = "otp_code")),
		@AttributeOverride(name = "status", column = @Column(name = "status")),
		@AttributeOverride(name = "sendAt", column = @Column(name = "send_at")),
		@AttributeOverride(name = "expireAt", column = @Column(name = "expire_at"))
	})
	private OtpRecord otpRecord;
	
}
