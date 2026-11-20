package com.jdc.auth.props;

import java.util.List;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Set;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthenticationProps {
	private Map<String, Resource> resources = Map.of();
	private Map<String, SystemDefinition> systems = Map.of();
	private String issuer = "http://localhost:8080";
	private long ticketLifetimeSeconds = 60;
	private Duration accessTokenTimeToLive = Duration.ofMinutes(10);
	private Duration refreshTokenTimeToLive = Duration.ofHours(8);
	private Map<String, Client> clients = Map.of();
	private ProfileDefaults registration;
	private ProfileDefaults activation;

	public String systemForClient(String clientId) {
		var client = clients.get(clientId);
		return client == null ? null : client.getSystem();
	}

	@Data
	public static class Client {
		private String system;
		private Set<String> scopes = Set.of();
		private List<String> audiences = List.of();
		private String secret;
	}

	public boolean allows(String system, String role) {
		var definition = system == null ? null : systems.get(system);
		return definition != null && role != null && definition.getRoles().contains(role);
	}

	public void validateClients() {
		for (var entry : systems.entrySet()) {
			if (entry.getKey().isBlank() || entry.getValue().getRoles().isEmpty()
					|| entry.getValue().getRoles().stream().anyMatch(String::isBlank)) {
				throw new IllegalStateException("System IDs and role lists must not be blank: " + entry.getKey());
			}
		}
		for (var entry : clients.entrySet()) {
			var client = entry.getValue();
			if (entry.getKey().isBlank() || client.getSystem() == null || !systems.containsKey(client.getSystem())
					|| client.getScopes().isEmpty() || client.getScopes().stream().anyMatch(String::isBlank)
					|| client.getAudiences().isEmpty() || client.getAudiences().stream().anyMatch(String::isBlank)) {
				throw new IllegalStateException("Client requires a known system, scopes, and audiences: " + entry.getKey());
			}
		}
		validateProfileDefaults(registration);
		validateProfileDefaults(activation);
		for (var entry : resources.entrySet()) {
			var resource = entry.getValue();
			if (entry.getKey().isBlank() || resource.getSecret() == null || resource.getSecret().isBlank()
					|| resource.getAudiences().isEmpty() || resource.getAudiences().stream().anyMatch(String::isBlank)) {
				throw new IllegalStateException("Resource requires a secret and audiences: " + entry.getKey());
			}
		}
	}

	private void validateProfileDefaults(ProfileDefaults defaults) {
		if (defaults != null && !allows(defaults.getSystem(), defaults.getRole())) {
			throw new IllegalStateException("Onboarding requires a configured system and permitted role.");
		}
	}

	@Data
	public static class ProfileDefaults {
		private String system;
		private String role;
	}

	@Data
	public static class SystemDefinition {
		private Set<String> roles = Set.of();
	}

	@Data
	public static class Resource {
		private String secret;
		private List<String> audiences = List.of();
	}
}
