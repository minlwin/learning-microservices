package com.jdc.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import com.jdc.auth.model.entity.*;
import com.jdc.auth.model.repo.LoginTicketRepo;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.AuthTestProperties;

class LoginTicketServiceTest {
	private final LoginTicketRepo repo = mock(LoginTicketRepo.class);
	private LoginTicketService service;
	private Profile profile;
	@BeforeEach void setup() {
		var settings = AuthTestProperties.create();
		settings.setClients(Map.of("consumer-demo", client("Consumer")));
		service = new LoginTicketService(repo, settings);
		profile = new Profile();
		profile.setId(UUID.randomUUID());
		profile.setSystem("Consumer");
		profile.setRole("Consumer");
		profile.setStatus(Profile.Status.Active);
	}
	@Test void storesHashAndConsumesOnlyOnce() {
		var result = service.issue(profile, "consumer-demo");
		assertTrue(result.loginTicket().matches("[A-Za-z0-9_-]{43}"));
		assertEquals(60, result.expiresIn());
		var captor = ArgumentCaptor.forClass(LoginTicket.class);
		verify(repo).save(captor.capture());
		var ticket = captor.getValue();
		assertTrue(ticket.getTokenHash().matches("[0-9a-f]{64}"));
		assertNotEquals(result.loginTicket(), ticket.getTokenHash());
		when(repo.findByTokenHash(ticket.getTokenHash())).thenReturn(Optional.of(ticket));
		assertThrows(BadCredentialsException.class, () -> service.consume(result.loginTicket(), "other-client"));
		assertNull(ticket.getConsumedAt());
		assertEquals(profile.getId(), service.consume(result.loginTicket(), "consumer-demo").profileId());
		assertNotNull(ticket.getConsumedAt());
		assertThrows(BadCredentialsException.class, () -> service.consume(result.loginTicket(), "consumer-demo"));
	}
	@Test void rejectsExpiredTicket() {
		var result = service.issue(profile, "consumer-demo");
		var captor = ArgumentCaptor.forClass(LoginTicket.class);
		verify(repo).save(captor.capture());
		var ticket = captor.getValue();
		ticket.setExpiresAt(Instant.EPOCH);
		when(repo.findByTokenHash(ticket.getTokenHash())).thenReturn(Optional.of(ticket));
		assertThrows(BadCredentialsException.class, () -> service.consume(result.loginTicket(), "consumer-demo"));
		assertNull(ticket.getConsumedAt());
	}
	@Test void rejectsTicketIssuedBeforeLogout() {
		var result = service.issue(profile, "consumer-demo");
		var captor = ArgumentCaptor.forClass(LoginTicket.class);
		verify(repo).save(captor.capture());
		var ticket = captor.getValue();
		when(repo.findByTokenHash(ticket.getTokenHash())).thenReturn(Optional.of(ticket));
		profile.setTokenVersion(profile.getTokenVersion() + 1);
		assertThrows(BadCredentialsException.class, () -> service.consume(result.loginTicket(), "consumer-demo"));
		assertNull(ticket.getConsumedAt());
	}

	@Test void issuesRestrictedTicketOnlyForPendingConsumerProfiles() {
		profile.setStatus(Profile.Status.Pending);
		var result = service.issueOnboarding(profile, "consumer-demo");
		var captor = ArgumentCaptor.forClass(LoginTicket.class);
		verify(repo).save(captor.capture());
		assertTrue(captor.getValue().isOnboarding());
		assertThrows(IllegalArgumentException.class, () -> service.issue(profile, "consumer-demo"));
		profile.setSystem("Management");
		assertThrows(IllegalArgumentException.class, () -> service.issueOnboarding(profile, "consumer-demo"));
		assertEquals(60, result.expiresIn());
	}

	@Test void acceptsAdministratorTicketOnlyForItsManagementClient() {
		var settings = AuthTestProperties.create();
		settings.setClients(Map.of("management-demo", client("Management"), "consumer-demo", client("Consumer")));
		var adminTickets = new LoginTicketService(repo, settings);
		profile.setSystem("Management");
		profile.setRole("Administrator");
		var result = adminTickets.issue(profile, "management-demo");
		var captured = ArgumentCaptor.forClass(LoginTicket.class);
		verify(repo).save(captured.capture());
		var ticket = captured.getValue();
		when(repo.findByTokenHash(ticket.getTokenHash())).thenReturn(Optional.of(ticket));
		assertThrows(BadCredentialsException.class, () -> adminTickets.consume(result.loginTicket(), "consumer-demo"));
		assertEquals(profile.getId(), adminTickets.consume(result.loginTicket(), "management-demo").profileId());
	}

	private static AuthenticationProps.Client client(String system) {
		var client = new AuthenticationProps.Client();
		client.setSystem(system);
		client.setScopes("Consumer".equals(system)
				? Set.of("consumer.api", "consumer.onboarding")
				: Set.of(system.toLowerCase(Locale.ROOT) + ".api"));
		client.setAudiences(List.of(system.toLowerCase(Locale.ROOT) + ".api"));
		client.setSecret("Consumer".equals(system) ? "consumer-demo-secret" : "management-demo-secret");
		return client;
	}
}
