package com.jdc.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.jdc.auth.model.entity.*;
import com.jdc.auth.model.repo.*;
import com.jdc.auth.props.AdministratorProps;

class AdministratorInitializerTest {
	private final ProfileRepo profiles = mock(ProfileRepo.class);
	private final IdentityRepo identities = mock(IdentityRepo.class);
	private final AdministratorProps properties = new AdministratorProps();
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	private AdministratorInitializer initializer;
	@BeforeEach void setup() {
		properties.setUsername("admin@example.com");
		properties.setPassword("admin-password");
		initializer = new AdministratorInitializer(profiles, identities, properties, encoder);
		when(identities.save(any(Identity.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}
	@Test void createsActiveAdministratorWithHashedPasswordOnFirstStartup() {
		initializer.run(null);
		var captured = ArgumentCaptor.forClass(Profile.class);
		verify(profiles).save(captured.capture());
		var administrator = captured.getValue();
		assertEquals("admin@example.com", administrator.getIdentity().getUsername());
		assertEquals(Profile.System.Management, administrator.getSystem());
		assertEquals(Profile.Role.Administrator, administrator.getRole());
		assertEquals(Profile.Status.Active, administrator.getStatus());
		assertNotEquals(properties.getPassword(), administrator.getPassword());
		assertTrue(encoder.matches(properties.getPassword(), administrator.getPassword()));
	}
	@Test void skipsWhenAnyProfileAlreadyExistsAndDoesNotResetCredentials() {
		when(profiles.count()).thenReturn(1L);
		properties.setPassword(null);
		initializer.initialize();
		verify(profiles, never()).save(any());
		verifyNoInteractions(identities);
	}
	@Test void reusesExistingIdentityWhenThereAreNoProfiles() {
		var identity = new Identity();
		identity.setUsername(properties.getUsername());
		when(identities.findOneByUsername(properties.getUsername())).thenReturn(Optional.of(identity));
		initializer.initialize();
		verify(identities, never()).save(any());
		var captured = ArgumentCaptor.forClass(Profile.class);
		verify(profiles).save(captured.capture());
		assertSame(identity, captured.getValue().getIdentity());
	}
	@Test void rejectsMissingCredentialsForEmptySystem() {
		properties.setPassword(" ");
		assertThrows(IllegalStateException.class, initializer::initialize);
		verify(profiles, never()).save(any());
		verifyNoInteractions(identities);
	}
}
