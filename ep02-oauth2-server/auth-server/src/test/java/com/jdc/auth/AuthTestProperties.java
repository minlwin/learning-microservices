package com.jdc.auth;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jdc.auth.props.AuthenticationProps;

public final class AuthTestProperties {
	private AuthTestProperties() {
	}

	public static AuthenticationProps create() {
		var properties = new AuthenticationProps();
		configure(properties);
		return properties;
	}

	public static void configure(AuthenticationProps properties) {
		var registration = new AuthenticationProps.ProfileDefaults();
		registration.setSystem("Consumer");
		registration.setRole("Consumer");
		registration.setClientId("consumer-demo");
		properties.setRegistration(registration);
		var activation = new AuthenticationProps.ProfileDefaults();
		activation.setSystem("Management");
		activation.setRole("Management");
		properties.setActivation(activation);
		properties.setSystems(Map.of("Consumer", system("Consumer"), "Management", system("Management", "Administrator")));
		var client = new AuthenticationProps.Client();
		client.setSystem("Consumer");
		client.setScopes(Set.of("consumer.api", "consumer.onboarding"));
		client.setAudiences(List.of("consumer.api"));
		client.setSecret("consumer-demo-secret");
		properties.setClients(Map.of("consumer-demo", client));
	}

	public static AuthenticationProps.SystemDefinition system(String... roles) {
		var definition = new AuthenticationProps.SystemDefinition();
		definition.setRoles(Set.of(roles));
		return definition;
	}

	public static AuthenticationProps.Resource resource(String secret, String... audiences) {
		var resource = new AuthenticationProps.Resource();
		resource.setSecret(secret);
		resource.setAudiences(List.of(audiences));
		return resource;
	}
}
