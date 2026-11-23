package com.jdc.auth.security;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.*;
import com.jdc.auth.service.LoginTicketService;

public class LoginTicketGrantProvider implements AuthenticationProvider {
	
	private final LoginTicketService tickets;
	private final OAuth2AuthorizationService authorizations;
	private final OAuth2TokenGenerator<?> generator;

	public LoginTicketGrantProvider(LoginTicketService tickets, OAuth2AuthorizationService authorizations,
			OAuth2TokenGenerator<?> generator) {
		this.tickets = tickets;
		this.authorizations = authorizations;
		this.generator = generator;
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		var grant = (LoginTicketGrantToken) authentication;
		if (!(grant.getPrincipal() instanceof OAuth2ClientAuthenticationToken client) || !client.isAuthenticated()) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
		}
		var registered = client.getRegisteredClient();
		if (registered == null || !registered.getAuthorizationGrantTypes().contains(LoginTicketGrantToken.GRANT_TYPE)) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
		}
		LoginTicketService.AuthenticatedLogin login;
		try {
			login = tickets.consume(grant.getTicket(), registered.getClientId());
		} catch (BadCredentialsException exception) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
		}
		grant.setTokenVersion(login.tokenVersion());
		grant.setOnboarding(login.onboarding());
		var principal = UsernamePasswordAuthenticationToken.authenticated(login.profileId().toString(), null,
				List.of());
		var scopes = login.onboarding() ? Set.of("consumer.onboarding") : registered.getScopes();
		var context = DefaultOAuth2TokenContext.builder().registeredClient(registered).principal(principal)
				.authorizationServerContext(AuthorizationServerContextHolder.getContext()).authorizedScopes(scopes)
				.authorizationGrantType(LoginTicketGrantToken.GRANT_TYPE).authorizationGrant(grant);
		var generated = generator.generate(context.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
		if (generated == null) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
		}
		var access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, generated.getTokenValue(),
				generated.getIssuedAt(), generated.getExpiresAt(), scopes);
		var authorization = OAuth2Authorization.withRegisteredClient(registered).principalName(principal.getName())
				.authorizationGrantType(LoginTicketGrantToken.GRANT_TYPE).authorizedScopes(scopes)
				.attribute(Principal.class.getName(), principal);
		if (generated instanceof ClaimAccessor claims) {
			authorization.token(access,
					metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims()));
		} else {
			authorization.accessToken(access);
		}
		OAuth2RefreshToken refresh = null;
		if (!login.onboarding()) {
			var token = generator.generate(context.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());
			if (!(token instanceof OAuth2RefreshToken refreshToken)) {
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
			}
			refresh = refreshToken;
			authorization.refreshToken(refresh);
		}
		authorizations.save(authorization.build());
		return new OAuth2AccessTokenAuthenticationToken(registered, client, access, refresh);
	}

	@Override
	public boolean supports(Class<?> type) {
		return LoginTicketGrantToken.class.isAssignableFrom(type);
	}
}
