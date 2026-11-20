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
		properties.setSystems(Map.of("Consumer", system("Consumer"), "Management", system("Management", "Administrator")));
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
