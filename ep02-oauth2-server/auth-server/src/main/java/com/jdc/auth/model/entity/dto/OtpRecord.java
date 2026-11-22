package com.jdc.auth.model.entity.dto;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class OtpRecord {

	public enum CheckStatus {
		Send, Success, Fail
	}

	@Column(nullable = false)
	private String otpCode;
	
	@Column(nullable = false)
	private CheckStatus status;
	
	@Column(nullable = false)
	private Instant sendAt;

	@Column(nullable = false)
	private Instant expireAt;
	
	private Instant checkedAt;

	private int sendCount;
	private int failCount;

}
