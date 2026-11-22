package com.jdc.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.jdc.management.props.AuthenticationProps;

class AuthenticationPropsTest {
	@Configuration
	@EnableConfigurationProperties(AuthenticationProps.class)
	static class Config {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(Config.class)
			.withPropertyValues("app.auth.resource-id=service", "app.auth.resource-secret=secret", "app.auth.audiences=one.api,two.api");

	@Test
	void bindsAudiencesAndDurationSettings() {
		runner.withPropertyValues("app.auth.connect-timeout=2s", "app.auth.read-timeout=5s").run(context -> {
			assertThat(context).hasNotFailed();
			var properties = context.getBean(AuthenticationProps.class);
			assertThat(properties.getAudiences()).containsExactly("one.api", "two.api");
			assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
			assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
		});
	}

	@Test
	void rejectsBlankCredentialsAndNonPositiveTimeouts() {
		runner.withPropertyValues("app.auth.resource-secret=").run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("app.auth.read-timeout=0s").run(context -> assertThat(context).hasFailed());
	}
}
