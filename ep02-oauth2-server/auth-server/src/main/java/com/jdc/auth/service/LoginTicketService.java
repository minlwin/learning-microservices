package com.jdc.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jdc.auth.api.output.AuthenticationResult;
import com.jdc.auth.model.entity.LoginTicket;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.LoginTicketRepo;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.security.ProfileAccess;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginTicketService {
	public record AuthenticatedLogin(UUID profileId, long tokenVersion) { }
	private final LoginTicketRepo repo;
	private final AuthenticationProps properties;
	private final SecureRandom random = new SecureRandom();

	@Transactional
	public AuthenticationResult issue(Profile profile, String clientId) {
		long lifetime = properties.getTicketLifetimeSeconds();
		if(lifetime <= 0) {
			throw new IllegalStateException("Login ticket lifetime must be positive.");
		}
		var bytes = new byte[32];
		random.nextBytes(bytes);
		var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		var ticket = new LoginTicket();
		ticket.setTokenHash(hash(value));
		ticket.setProfile(profile);
		ticket.setTokenVersion(profile.getTokenVersion());
		ticket.setClientId(clientId);
		ticket.setExpiresAt(Instant.now().plusSeconds(lifetime));
		repo.save(ticket);
		return new AuthenticationResult(value, lifetime);
	}

	/**
	 * Atomically consumes a ticket. A future token grant must authenticate or
	 * otherwise validate the client before calling this method.
	 */
	@Transactional
	public AuthenticatedLogin consume(String value, String clientId) {
		if(value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
			throw invalidTicket();
		}
		var ticket = repo.findByTokenHash(hash(value)).orElseThrow(this::invalidTicket);
		var system = properties.systemForClient(clientId);
		if(ticket.getConsumedAt() != null || !ticket.getExpiresAt().isAfter(Instant.now())
				|| !ticket.getClientId().equals(clientId) || system == null
				|| ticket.getTokenVersion() != ticket.getProfile().getTokenVersion()
				|| !ProfileAccess.allows(system, ticket.getProfile(), properties)
				|| ticket.getProfile().getStatus() != Profile.Status.Active) {
			throw invalidTicket();
		}
		ticket.setConsumedAt(Instant.now());
		return new AuthenticatedLogin(ticket.getProfile().getId(), ticket.getTokenVersion());
	}

	private BadCredentialsException invalidTicket() {
		return new BadCredentialsException("Invalid or expired login ticket.");
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch(NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
