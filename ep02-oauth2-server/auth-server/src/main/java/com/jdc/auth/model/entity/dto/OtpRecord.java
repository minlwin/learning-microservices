package com.jdc.auth.model.entity.dto;

import java.time.Instant;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class OtpRecord {

	public enum CheckStatus {
		Send, Success, Fail
	}

	private String otpCode;
	
	private CheckStatus status;
	
	private Instant sendAt;

	private Instant expireAt;
	
	private Instant checkedAt;

	private int sendCount;
	private int failCount;

}
