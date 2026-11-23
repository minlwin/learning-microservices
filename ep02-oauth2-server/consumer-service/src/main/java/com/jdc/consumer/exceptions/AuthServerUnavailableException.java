package com.jdc.consumer.exceptions;

public class AuthServerUnavailableException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AuthServerUnavailableException() {
		super("Unable to verify the Consumer profile with auth-server. Try again later.");
	}

	public AuthServerUnavailableException(Throwable cause) {
		super("Unable to verify the Consumer profile with auth-server. Try again later.", cause);
	}
}
