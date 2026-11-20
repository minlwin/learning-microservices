package com.jdc.auth;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.service.TokenValidationService;

@Configuration
public class SessionSecurityConfig {
	@Bean
	@Order(2)
	SecurityFilterChain logoutSecurity(HttpSecurity http, TokenValidationService tokens,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) throws Exception {
		http.securityMatcher("/api/logout");
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.csrf(AbstractHttpConfigurer::disable);
		http.formLogin(AbstractHttpConfigurer::disable);
		http.httpBasic(AbstractHttpConfigurer::disable);
		http.logout(AbstractHttpConfigurer::disable);
		http.requestCache(AbstractHttpConfigurer::disable);
		http.oauth2ResourceServer(resource -> {
			resource.opaqueToken(opaque -> opaque.introspector(tokens::validate));
			resource.authenticationEntryPoint((request, response, exception) -> errors.resolveException(request, response, null, exception));
			resource.accessDeniedHandler((request, response, exception) -> errors.resolveException(request, response, null, exception));
		});
		http.authorizeHttpRequests(requests -> {
			requests.requestMatchers(HttpMethod.POST, "/api/logout").authenticated();
			requests.anyRequest().denyAll();
		});
		return http.build();
	}

	@Bean
	@Order(3)
	SecurityFilterChain internalTokenSecurity(HttpSecurity http, AuthenticationProps properties,
			PasswordEncoder encoder, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) throws Exception {
		var users = new ArrayList<UserDetails>();
		
		for (var entry : properties.getResourceSecrets().entrySet()) {
			if (entry.getValue().isBlank()) {
				throw new IllegalStateException("Missing resource-server secret.");
			}
			var user = User.withUsername(entry.getKey());
			user.password(encoder.encode(entry.getValue()));
			user.roles("RESOURCE_SERVER");
			users.add(user.build());
		}
		
		var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(users));
		provider.setPasswordEncoder(encoder);
		http.securityMatcher("/api/internal/**");
		http.authenticationManager(new ProviderManager(provider));
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.csrf(AbstractHttpConfigurer::disable);
		http.formLogin(AbstractHttpConfigurer::disable);
		http.logout(AbstractHttpConfigurer::disable);
		http.requestCache(AbstractHttpConfigurer::disable);
		http.httpBasic(basic -> basic.authenticationEntryPoint((request, response, _) ->
				errors.resolveException(request, response, null, new BadCredentialsException("Resource-server authentication is required."))));
		http.authorizeHttpRequests(requests -> {
			requests.requestMatchers(HttpMethod.POST, "/api/internal/tokens/introspect").hasRole("RESOURCE_SERVER");
			requests.anyRequest().denyAll();
		});
		http.exceptionHandling(exceptions -> {
			exceptions.authenticationEntryPoint((request, response, _) -> errors.resolveException(request, response, null,
					new BadCredentialsException("Resource-server authentication is required.")));
			exceptions.accessDeniedHandler((request, response, exception) -> errors.resolveException(request, response, null, exception));
		});
		return http.build();
	}
}
