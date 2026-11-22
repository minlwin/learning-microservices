package com.jdc.management.exceptions;

public class BusinessRuleViolationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BusinessRuleViolationException(String message, Throwable cause) {
		super(message, cause);
	}

	public BusinessRuleViolationException(String message) {
		super(message);
	}

}
