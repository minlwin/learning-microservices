package com.jdc.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
				"app.auth.refresh-token-time-to-live", "PT12H",
				"app.auth.clients.consumer-demo.system", "Consumer",
				"app.auth.clients.consumer-demo.secret", "consumer-demo-secret",
				"app.auth.clients.consumer-demo.scopes[0]", "consumer.api",
				"app.auth.clients.consumer-demo.audiences[0]", "consumer.api"));
		var properties = new Binder(source).bind("app.auth", AuthenticationProps.class).get();
		AuthTestProperties.configure(properties);

		var clients = new AuthorizationServerConfig().registeredClients(properties, new BCryptPasswordEncoder(), JdbcTestSupport.createDatabase());
		var settings = clients.findByClientId("consumer-demo").getTokenSettings();

		assertEquals(Duration.ofMinutes(15), settings.getAccessTokenTimeToLive());
		assertEquals(Duration.ofHours(12), settings.getRefreshTokenTimeToLive());
	}
	@Test
	void updatesPersistedClientsWithoutChangingTheirIdOrUnchangedSecret() {
		var source = new MapConfigurationPropertySource(Map.of(
				"app.auth.clients.consumer-demo.system", "Consumer",
				"app.auth.clients.consumer-demo.secret", "first-secret",
				"app.auth.clients.consumer-demo.scopes[0]", "consumer.api",
				"app.auth.clients.consumer-demo.audiences[0]", "consumer.api"));
		var properties = new Binder(source).bind("app.auth", AuthenticationProps.class).get();
		AuthTestProperties.configure(properties);
		var jdbc = JdbcTestSupport.createDatabase();
		var config = new AuthorizationServerConfig();
		var encoder = new BCryptPasswordEncoder();
		var first = config.registeredClients(properties, encoder, jdbc).findByClientId("consumer-demo");
		var second = config.registeredClients(properties, encoder, jdbc).findByClientId("consumer-demo");
		assertEquals(first.getId(), second.getId());
		assertEquals(first.getClientSecret(), second.getClientSecret());

		properties.getClients().get("consumer-demo").setSecret("changed-secret");
		properties.setAccessTokenTimeToLive(Duration.ofMinutes(20));
		var updated = config.registeredClients(properties, encoder, jdbc).findByClientId("consumer-demo");
		assertEquals(first.getId(), updated.getId());
		assertEquals(Duration.ofMinutes(20), updated.getTokenSettings().getAccessTokenTimeToLive());
		assertTrue(encoder.matches("changed-secret", updated.getClientSecret()));
	}
	@Test
	void bindsNewSystemClientAndResourceWithoutEnumChanges() {
		var source = new MapConfigurationPropertySource(Map.of(
				"app.auth.systems.Merchant.roles[0]", "MerchantOperator",
				"app.auth.clients.merchant-mobile.system", "Merchant",
				"app.auth.clients.merchant-mobile.secret", "merchant-secret",
				"app.auth.clients.merchant-mobile.scopes[0]", "orders.read",
				"app.auth.clients.merchant-mobile.audiences[0]", "orders.api",
				"app.auth.resources.orders-service.secret", "orders-secret",
				"app.auth.resources.orders-service.audiences[0]", "orders.api"));
		var properties = new Binder(source).bind("app.auth", AuthenticationProps.class).get();
		properties.validateClients();
		assertTrue(properties.allows("Merchant", "MerchantOperator"));
		assertEquals("Merchant", properties.systemForClient("merchant-mobile"));
		var clients = new AuthorizationServerConfig().registeredClients(properties, new BCryptPasswordEncoder(), JdbcTestSupport.createDatabase());
		assertTrue(clients.findByClientId("merchant-mobile").getScopes().contains("orders.read"));
		properties.getClients().get("merchant-mobile").setSystem("UnknownSystem");
		assertThrows(IllegalStateException.class, properties::validateClients);
	}
}
