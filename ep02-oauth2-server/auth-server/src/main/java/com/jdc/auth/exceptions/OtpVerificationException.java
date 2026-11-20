package com.jdc.auth.exceptions;

/** A rejected OTP attempt whose failure counter must still be committed. */
public class OtpVerificationException extends BusinessRuleViolationException {

	private static final long serialVersionUID = 1L;

	public OtpVerificationException(String message) {
		super(message);
	}
}
