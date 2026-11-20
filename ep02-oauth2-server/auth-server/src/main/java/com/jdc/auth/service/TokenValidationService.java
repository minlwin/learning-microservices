package com.jdc.auth.service;

import org.springframework.security.core.GrantedAuthority;

import java.util.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.security.ProfileAccess;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.model.repo.ProfileRepo;
import lombok.RequiredArgsConstructor;

/**
 * Exact token lookup proves provenance; profile version provides live
 * revocation.
 */
@Service
@RequiredArgsConstructor
public class TokenValidationService {
	private final OAuth2AuthorizationService authorizations;
	private final ProfileRepo profiles;
	private final AuthenticationProps properties;

	@Transactional(readOnly = true)
	public OAuth2AuthenticatedPrincipal validate(String token) {
		var authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
		if (authorization == null || authorization.getAccessToken() == null
				|| !authorization.getAccessToken().isActive()) {
			throw invalid();
		}
		var claims = authorization.getAccessToken().getClaims();
		if (claims == null || !(claims.get("token_version") instanceof Number version)) {
			throw invalid();
		}
		UUID id;
		
		try {
			id = UUID.fromString(authorization.getPrincipalName());
		} catch (IllegalArgumentException exception) {
			throw invalid();
		}
		
		var profile = profiles.findById(id).orElseThrow(this::invalid);
		if (profile.getStatus() != Profile.Status.Active || profile.getTokenVersion() != version.longValue()
				|| !ProfileAccess.allows(profile.getSystem(), profile, properties)) {
			throw invalid();
		}
		var attributes = new HashMap<String, Object>(claims);
		attributes.put("sub", id.toString());
		attributes.put("scope", String.join(" ", authorization.getAuthorizedScopes()));
		var scopeAuthorities = authorization.getAuthorizedScopes()
				.stream().<GrantedAuthority>map(
						scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
				.toList();
		var authorities = new ArrayList<GrantedAuthority>(scopeAuthorities);
		var role = profile.getRole();
		attributes.put("roles", List.of(role));
		authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
		return new DefaultOAuth2AuthenticatedPrincipal(id.toString(), attributes, authorities);
	}

	private BadCredentialsException invalid() {
		return new BadCredentialsException("Invalid or revoked access token.");
	}
}
