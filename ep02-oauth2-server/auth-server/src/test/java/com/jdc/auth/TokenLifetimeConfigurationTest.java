package com.jdc.auth;

import com.jdc.auth.model.entity.Profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.jdc.auth.props.AuthenticationProps;

class TokenLifetimeConfigurationTest {

	@Test
	void bindsConfiguredDurationsAndAppliesThemToRegisteredClients() {
		var source = new MapConfigurationPropertySource(Map.of(
				"app.auth.access-token-time-to-live", "15m",
				"app.auth.refresh-token-time-to-live", "PT12H"));
		var properties = new Binder(source).bind("app.auth", AuthenticationProps.class).get();
		properties.setClients(Map.of("consumer-demo", Profile.System.Consumer));
		properties.setClientSecrets(Map.of("consumer-demo", "test-secret"));

		var clients = new AuthorizationServerConfig().registeredClients(properties, new BCryptPasswordEncoder());
		var settings = clients.findByClientId("consumer-demo").getTokenSettings();

		assertEquals(Duration.ofMinutes(15), settings.getAccessTokenTimeToLive());
		assertEquals(Duration.ofHours(12), settings.getRefreshTokenTimeToLive());
	}
}
