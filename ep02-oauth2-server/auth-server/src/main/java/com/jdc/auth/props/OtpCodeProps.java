package com.jdc.auth.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Data
@Validated
@ConfigurationProperties(prefix = "app.otp")
public class OtpCodeProps {

	@Valid
	private Expiration expiration = new Expiration();
	@Valid
	private Cooldown cooldown = new Cooldown();
	@Valid
	private Resend resend = new Resend();


	@Data
	public static class Expiration {
		@Min(1)
		private int registration = 3;
		@Min(1)
		private int activation = 5;
	}

	@Data
	public static class Cooldown {
		@Min(1)
		private int missingCount = 3;
		@Min(1)
		private int waitingDuration = 30;
	}

	@Data
	public static class Resend {
		@Min(1)
		private int intervalSeconds = 60;
	}
}
