package com.jdc.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.jdc.auth.api.input.AuthenticationForm;
import com.jdc.auth.api.output.AuthenticationResult;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.AuthTestProperties;

class AuthenticationServiceTest {
	private final ProfileRepo profiles = mock(ProfileRepo.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);
	private final LoginTicketService tickets = mock(LoginTicketService.class);
	private final AuthenticationForm form = new AuthenticationForm("consumer-demo", "user@example.com", "password");
	private AuthenticationService service;
	private Profile profile;
	@BeforeEach
	void setup() {
		var properties = AuthTestProperties.create();
		properties.setClients(Map.of("consumer-demo", client("Consumer")));
		when(encoder.encode(anyString())).thenReturn("dummy-hash");
		service = new AuthenticationService(profiles, encoder, tickets, properties);
		profile = new Profile();
		profile.setSystem("Consumer");
		profile.setRole("Consumer");
		profile.setStatus(Profile.Status.Active);
		profile.setPassword("hash");
		when(profiles.findByIdentityUsernameAndSystem(form.username(), "Consumer")).thenReturn(Optional.of(profile));
		when(encoder.matches("password", "hash")).thenReturn(true);
	}
	@Test void returnsTicketForActiveProfile() {
		var result = new AuthenticationResult("ticket", 60);
		when(tickets.issue(profile, form.clientId())).thenReturn(result);
		assertEquals(result, service.authenticate(form));
	}
	@Test void rejectsWrongPassword() {
		when(encoder.matches("password", "hash")).thenReturn(false);
		assertThrows(BadCredentialsException.class, () -> service.authenticate(form));
		verifyNoInteractions(tickets);
	}
	@Test void rejectsAllInactiveStates() {
		for(var state : new Profile.Status[]{Profile.Status.Pending, Profile.Status.Locked, Profile.Status.Closed}) {
			profile.setStatus(state);
			assertThrows(BadCredentialsException.class, () -> service.authenticate(form));
		}
		verifyNoInteractions(tickets);
	}
	@Test void rejectsUnknownClient() {
		assertThrows(BadCredentialsException.class, () -> service.authenticate(new AuthenticationForm("unknown", form.username(), form.password())));
		verifyNoInteractions(tickets, profiles);
	}
	@Test void checksDummyHashForUnknownUser() {
		when(profiles.findByIdentityUsernameAndSystem(form.username(), "Consumer")).thenReturn(Optional.empty());
		assertThrows(BadCredentialsException.class, () -> service.authenticate(form));
		verify(encoder).matches("password", "dummy-hash");
		verifyNoInteractions(tickets);
	}
	@Test void administratorCanSignInThroughManagementClientButNotConsumerClient() {
		var settings = AuthTestProperties.create();
		settings.setClients(Map.of("management-demo", client("Management"), "consumer-demo", client("Consumer")));
		var managementService = new AuthenticationService(profiles, encoder, tickets, settings);
		profile.setSystem("Management");
		profile.setRole("Administrator");
		when(profiles.findByIdentityUsernameAndSystem(form.username(), "Management")).thenReturn(Optional.of(profile));
		var result = new AuthenticationResult("admin-ticket", 60);
		when(tickets.issue(profile, "management-demo")).thenReturn(result);
		assertEquals(result, managementService.authenticate(new AuthenticationForm("management-demo", form.username(), form.password())));
		when(profiles.findByIdentityUsernameAndSystem(form.username(), "Consumer")).thenReturn(Optional.empty());
		assertThrows(BadCredentialsException.class, () -> managementService.authenticate(form));
		verify(tickets, never()).issue(profile, "consumer-demo");
	}

	@Test void rejectsAdministratorRoleOnConsumerProfile() {
		profile.setRole("Administrator");
		assertThrows(BadCredentialsException.class, () -> service.authenticate(form));
		verifyNoInteractions(tickets);
	}

	private static AuthenticationProps.Client client(String system) {
		var client = new AuthenticationProps.Client();
		client.setSystem(system);
		client.setScopes(Set.of(system.toLowerCase(Locale.ROOT) + ".api"));
		client.setAudiences(List.of(system.toLowerCase(Locale.ROOT) + ".api"));
		client.setSecret("Consumer".equals(system) ? "consumer-demo-secret" : "management-demo-secret");
		return client;
	}
}
