package com.jdc.auth.security;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

public class LoginTicketGrantToken extends OAuth2AuthorizationGrantAuthenticationToken {

	private static final long serialVersionUID = 1L;

	public static final AuthorizationGrantType GRANT_TYPE = new AuthorizationGrantType(
			"urn:jdc:params:oauth:grant-type:login-ticket");

	private final String ticket;
	private long tokenVersion;

	public LoginTicketGrantToken(String ticket, Authentication client) {
		super(GRANT_TYPE, client, Map.of());
		this.ticket = ticket;
	}

	public String getTicket() {
		return ticket;
	}

	public long getTokenVersion() {
		return tokenVersion;
	}

	public void setTokenVersion(long tokenVersion) {
		this.tokenVersion = tokenVersion;
	}

}
