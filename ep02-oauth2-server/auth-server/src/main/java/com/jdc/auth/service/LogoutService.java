package com.jdc.auth.service;

import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jdc.auth.model.repo.ProfileRepo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LogoutService {
	private final ProfileRepo profiles;

	/** Invalidates all token generations for this profile without affecting other systems. */
	@Transactional
	public void logout(UUID profileId, long expectedVersion) {
		if (profiles.revokeTokens(profileId, expectedVersion) != 1) {
			throw new BadCredentialsException("This session has already been revoked.");
		}
	}
}
