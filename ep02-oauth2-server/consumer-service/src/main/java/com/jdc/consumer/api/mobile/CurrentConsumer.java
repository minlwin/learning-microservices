package com.jdc.consumer.api.mobile;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

final class CurrentConsumer {
	private CurrentConsumer() {
	}

	static String username(OAuth2AuthenticatedPrincipal principal) {
		Object username = principal == null ? null : principal.getAttribute("username");
		if (!(username instanceof String value) || value.isBlank()) {
			throw new BadCredentialsException("The authenticated identity has no username.");
		}
		return value;
	}
}
