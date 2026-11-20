package com.jdc.auth.props;

import java.util.List;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.jdc.auth.model.entity.Profile;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthenticationProps {
	private Map<String, String> resourceSecrets = Map.of();
	private Map<String, List<String>> resourceAudiences = Map.of();
	private String issuer = "http://localhost:8080";
	private Map<String, String> clientSecrets = Map.of();
	private long ticketLifetimeSeconds = 60;
	private Duration accessTokenTimeToLive = Duration.ofMinutes(10);
	private Duration refreshTokenTimeToLive = Duration.ofHours(8);
	private Map<String, Profile.System> clients = Map.of();
}
