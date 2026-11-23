package com.jdc.auth.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.jdc.auth.model.entity.Identity;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.IdentityRepo;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.props.AdministratorProps;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.service.PasswordPolicy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/** Bootstraps the first account without registration or activation. */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdministratorProps.class)
public class AdministratorInitializer implements ApplicationRunner {

	private final ProfileRepo profiles;
	private final IdentityRepo identities;
	private final AdministratorProps properties;
	private final AuthenticationProps authenticationProperties;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		initialize();
	}

	@Transactional
	public void initialize() {
		// Profiles represent user accounts. Never reset credentials on later starts.
		if (profiles.count() != 0) {
			return;
		}
		var username = properties.getUsername();
		var password = properties.getPassword();
		
		if (username == null || username.isBlank() || password == null || password.isBlank()) {
			throw new IllegalStateException("Administrator username and password must be configured for the first startup.");
		}
		PasswordPolicy.validate(password);

		if (!authenticationProperties.allows(properties.getSystem(), properties.getRole())) {
			throw new IllegalStateException("Administrator requires a configured system and permitted role.");
		}

		var identity = identities.findOneByUsername(username).orElseGet(() -> {
			var created = new Identity();
			created.setUsername(username);
			return identities.save(created);
		});

		var administrator = new Profile();
		administrator.setIdentity(identity);
		administrator.setSystem(properties.getSystem());
		administrator.setRole(properties.getRole());
		administrator.setStatus(Profile.Status.Active);
		administrator.setPassword(passwordEncoder.encode(password));
		profiles.save(administrator);
	}
}
