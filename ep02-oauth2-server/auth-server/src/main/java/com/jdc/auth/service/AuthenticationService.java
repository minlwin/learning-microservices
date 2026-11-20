package com.jdc.auth.service;

import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jdc.auth.api.input.AuthenticationForm;
import com.jdc.auth.api.output.AuthenticationResult;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.security.ProfileAccess;

@Service
public class AuthenticationService {
	
	private final ProfileRepo profiles;
	private final PasswordEncoder encoder;
	private final LoginTicketService tickets;
	private final AuthenticationProps properties;
	private final String dummyHash;

	public AuthenticationService(ProfileRepo profiles, PasswordEncoder encoder,
			LoginTicketService tickets, AuthenticationProps properties) {
		this.profiles = profiles;
		this.encoder = encoder;
		this.tickets = tickets;
		this.properties = properties;
		this.dummyHash = encoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public AuthenticationResult authenticate(AuthenticationForm form) {
		
		var system = properties.systemForClient(form.clientId());
		
		if(system == null) {
			throw new BadCredentialsException("Invalid login credentials.");
		}
		
		var profile = profiles.findByIdentityUsernameAndSystem(form.username(), system);
		
		// Check a dummy hash for unknown usernames to avoid an early password-check bypass.
		boolean matches = encoder.matches(form.password(), profile.map(Profile::getPassword).orElse(dummyHash));
		
		if(!matches || profile.isEmpty() || profile.get().getStatus() != Profile.Status.Active
				|| !ProfileAccess.allows(system, profile.get(), properties)) {
			throw new BadCredentialsException("Invalid login credentials.");
		}
		
		return tickets.issue(profile.get(), form.clientId());
	}
}
