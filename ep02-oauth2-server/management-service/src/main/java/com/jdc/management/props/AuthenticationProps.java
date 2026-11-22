package com.jdc.management.props;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthenticationProps {
	@NotBlank
	private String introspectionUri = "http://localhost:8080/api/internal/tokens/introspect";
	@NotBlank
	private String issuer = "http://localhost:8080";
	@NotBlank
	private String resourceId;
	@NotBlank
	private String resourceSecret;
	@NotEmpty
	private List<@NotBlank String> audiences = List.of();
	@NotNull
	private Duration connectTimeout = Duration.ofSeconds(3);
	@NotNull
	private Duration readTimeout = Duration.ofSeconds(3);

	@AssertTrue(message = "HTTP timeouts must be positive.")
	public boolean isTimeoutsValid() {
		return connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero()
				&& readTimeout != null && !readTimeout.isNegative() && !readTimeout.isZero();
	}
}
