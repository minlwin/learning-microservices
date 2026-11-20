package com.jdc.consumer;

import java.util.ArrayList;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
public class ResourceSecurityConfig {
	@Bean
	RestClient.Builder introspectionHttpBuilder(
			@Value("${auth.resource-id:consumer-service}") String resource,
			@Value("${auth.resource-secret}") String secret) {
		var client = HttpClient.newBuilder();
		client.connectTimeout(Duration.ofSeconds(3));
		var requests = new JdkClientHttpRequestFactory(client.build());
		requests.setReadTimeout(Duration.ofSeconds(3));
		var http = RestClient.builder();
		http.requestFactory(requests);
		http.defaultHeaders(headers -> headers.setBasicAuth(resource, secret));
		return http;
	}

	@Bean
	RestClient introspectionHttp(
			@Qualifier("introspectionHttpBuilder") RestClient.Builder builder) {
		return builder.build();
	}

	@Bean
	OpaqueTokenIntrospector liveTokenIntrospector(
			@Value("${auth.introspection-uri:http://localhost:8080/api/internal/tokens/introspect}") String uri,
			@Qualifier("introspectionHttp") RestClient http,
			@Value("${auth.issuer:http://localhost:8080}") String issuer,
			@Value("${auth.audiences:consumer.api,management.api}") String allowedAudienceValues) {
		var allowedAudiences = Set.of(allowedAudienceValues.split("\\s*,\\s*"));
		var remote = new RestClientOpaqueTokenIntrospector(uri, http);
		// No cache: every request observes the auth server's latest token version.
		return token -> {
			var principal = remote.introspect(token);
			Object tokenIssuer = principal.getAttribute("iss");
			Object audience = principal.getAttribute("aud");
			if (tokenIssuer == null || !issuer.equals(tokenIssuer.toString())
					|| !(audience instanceof Collection<?> values)
					|| values.stream().noneMatch(allowedAudiences::contains)) {
				throw new BadOpaqueTokenException("Invalid issuer or audience.");
			}
			var authorities = new ArrayList<GrantedAuthority>(principal.getAuthorities());
			Object roles = principal.getAttribute("roles");
			if (roles instanceof Collection<?> roleNames) {
				for (var role : roleNames) {
					if (role instanceof String name && !name.isBlank()) {
						authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
					}
				}
			}
			return new DefaultOAuth2AuthenticatedPrincipal(
					principal.getName(), principal.getAttributes(), authorities);
		};
	}

	@Bean
	SecurityFilterChain resourceSecurity(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.csrf(AbstractHttpConfigurer::disable);
		http.formLogin(AbstractHttpConfigurer::disable);
		http.httpBasic(AbstractHttpConfigurer::disable);
		http.logout(AbstractHttpConfigurer::disable);
		http.requestCache(AbstractHttpConfigurer::disable);
		http.oauth2ResourceServer(resource -> resource.opaqueToken(opaque -> opaque.introspector(introspector)));
		http.authorizeHttpRequests(requests -> {
			requests.requestMatchers("/api/management/**").hasAuthority("SCOPE_management.api");
			requests.requestMatchers("/api/self/**").hasAuthority("SCOPE_consumer.api");
			requests.anyRequest().denyAll();
		});
		return http.build();
	}
}
