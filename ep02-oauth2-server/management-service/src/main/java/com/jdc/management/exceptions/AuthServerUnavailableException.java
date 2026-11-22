package com.jdc.management.exceptions;

public class AuthServerUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuthServerUnavailableException() {
        super("Unable to verify email availability with auth-server. Try again later.");
    }
}
