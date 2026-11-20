package com.jdc.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationConverter;

import jakarta.servlet.http.HttpServletRequest;

public class LoginTicketGrantConverter implements AuthenticationConverter {
	@Override
	public Authentication convert(HttpServletRequest request) {
		if(!LoginTicketGrantToken.GRANT_TYPE.getValue().equals(request.getParameter("grant_type"))) {
			return null;
		}
		var grants = request.getParameterValues("grant_type");
		var values = request.getParameterValues("login_ticket");
		if(grants.length != 1 || values == null || values.length != 1 || values[0].isBlank()
				|| request.getParameter("scope") != null) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
		}
		return new LoginTicketGrantToken(values[0], SecurityContextHolder.getContext().getAuthentication());
	}
}
