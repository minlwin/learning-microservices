package com.jdc.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

	@Bean
	@Order(4)
	SecurityFilterChain apiSecurity(HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(requests -> {
					requests.requestMatchers(HttpMethod.POST,
							"/api/auth",
							"/api/registration",
							"/api/registration/**",
							"/api/activate/**").permitAll();
					requests.anyRequest().denyAll();
				})
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, _) -> resolver.resolveException(request, response, null,
								new AccessDeniedException("This endpoint is disabled.")))
						.accessDeniedHandler((request, response, exception) -> resolver.resolveException(request, response, null, exception)));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
