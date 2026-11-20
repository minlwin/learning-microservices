package com.jdc.auth.api.output;

/** A one-time login credential, not an OpenID Connect ID token. */
public record AuthenticationResult(String loginTicket, long expiresIn) {
}
