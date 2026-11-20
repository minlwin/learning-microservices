package com.jdc.auth.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.otp")
public class OtpCodeProps {
	
	private Expiration expiration;
	private Cooldown cooldown;
	private Resend resend;

	
	@Data
	public static class Expiration {
		private int registration;
		private int activation; 
	}
	
	@Data
	public static class Cooldown {
		private int missingCount;
		private int waitingDuration;
	}
	
	@Data
	public static class Resend {
		private int intervalSeconds;
	}
}
