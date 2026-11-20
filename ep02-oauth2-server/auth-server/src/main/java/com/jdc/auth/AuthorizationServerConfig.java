package com.jdc.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.exceptions.handler.ExceptionHandlers;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.security.*;
import com.jdc.auth.service.LoginTicketService;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class AuthorizationServerConfig {

	@Bean
	@Order(1)
	SecurityFilterChain tokenEndpointSecurity(HttpSecurity http, LoginTicketService tickets,
			OAuth2AuthorizationService authorizations, OAuth2TokenGenerator<?> generator, ExceptionHandlers handlers)
			throws Exception {
		// Restrict the entire chain, not just authorization rules: protocol filters
		// can otherwise serve metadata, keys, and other endpoints before authorization.
		var tokenEndpointMatcher = PathPatternRequestMatcher.withDefaults()
				.matcher(HttpMethod.POST, "/oauth2/token");
		var authorizationServer = new OAuth2AuthorizationServerConfigurer();
		var ticketGrantProvider = new LoginTicketGrantProvider(tickets, authorizations, generator);

		http.securityMatcher(tokenEndpointMatcher);
		
		http.with(authorizationServer, server -> {
			
			server.clientAuthentication(client -> client.errorResponseHandler(handlers::oauth2Failure));
			
			server.tokenEndpoint(endpoint -> {
				endpoint.errorResponseHandler(handlers::oauth2Failure);
				endpoint.accessTokenRequestConverter(new LoginTicketGrantConverter());
				endpoint.authenticationProvider(ticketGrantProvider);
			});
		});
		
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		
		http.csrf(AbstractHttpConfigurer::disable);
		http.formLogin(AbstractHttpConfigurer::disable);
		http.httpBasic(AbstractHttpConfigurer::disable);
		http.logout(AbstractHttpConfigurer::disable);
		http.requestCache(AbstractHttpConfigurer::disable);
		http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
		
		return http.build();
	}

	@Bean
	RegisteredClientRepository registeredClients(AuthenticationProps properties, PasswordEncoder encoder) {
		var clients = new ArrayList<RegisteredClient>();

		for (var entry : properties.getClients().entrySet()) {
			var clientId = entry.getKey();
			var system = entry.getValue().name();
			var secret = properties.getClientSecrets().get(clientId);
			if (secret == null || secret.isBlank()) {
				throw new IllegalStateException("Missing client secret for " + clientId);
			}

			var clientSettings = ClientSettings.builder();
			clientSettings.setting("app.system", system);

			var tokenSettings = TokenSettings.builder();
			tokenSettings.accessTokenTimeToLive(properties.getAccessTokenTimeToLive());
			tokenSettings.refreshTokenTimeToLive(properties.getRefreshTokenTimeToLive());
			tokenSettings.reuseRefreshTokens(false);

			var client = RegisteredClient.withId(clientId);
			client.clientId(clientId);
			client.clientSecret(encoder.encode(secret));
			client.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
			client.authorizationGrantType(LoginTicketGrantToken.GRANT_TYPE);
			client.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
			client.scope(system.toLowerCase(Locale.ROOT) + ".api");
			client.clientSettings(clientSettings.build());
			client.tokenSettings(tokenSettings.build());

			clients.add(client.build());
		}
		return new InMemoryRegisteredClientRepository(clients);
	}

	@Bean
	OAuth2AuthorizationService authorizationService() {
		return new InMemoryOAuth2AuthorizationService();
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(AuthenticationProps properties) {
		var settings = AuthorizationServerSettings.builder();
		settings.issuer(properties.getIssuer());
		return settings.build();
	}

	@Bean
	JWKSource<SecurityContext> signingKeys() throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var pair = generator.generateKeyPair();
		var keyBuilder = new RSAKey.Builder((RSAPublicKey) pair.getPublic());
		keyBuilder.privateKey((RSAPrivateKey) pair.getPrivate());
		keyBuilder.keyID(UUID.randomUUID().toString());
		var key = keyBuilder.build();
		var keys = new JWKSet(key);
		return (selector, _) -> selector.select(keys);
	}

	@Bean
	OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> keys, ProfileRepo profiles) {
		var jwt = new JwtGenerator(new NimbusJwtEncoder(keys));
		
		jwt.setJwtCustomizer(context -> {
			var profile = profiles.findById(UUID.fromString(context.getPrincipal().getName()))
					.orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
			String system = context.getRegisteredClient().getClientSettings().getSetting("app.system");
			if (profile.getStatus() != Profile.Status.Active || !ProfileAccess.allows(Profile.System.valueOf(system), profile)) {
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
			}
			long expectedVersion;
			if (LoginTicketGrantToken.GRANT_TYPE.equals(context.getAuthorizationGrantType())) {
				LoginTicketGrantToken grant = context.getAuthorizationGrant();
				expectedVersion = grant.getTokenVersion();
			} else {
				var authorization = context.getAuthorization();
				Object version = authorization == null || authorization.getAccessToken() == null
						? null : authorization.getAccessToken().getClaims().get("token_version");
				if (!(version instanceof Number number)) {
					throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
				}
				expectedVersion = number.longValue();
			}
			if (expectedVersion != profile.getTokenVersion()) {
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
			}
			var claims = context.getClaims();
			claims.claim("token_version", expectedVersion);
			claims.claim("system", profile.getSystem().name());
			claims.claim("roles", List.of(profile.getRole().name()));
			claims.audience(List.of(system.toLowerCase(Locale.ROOT) + ".api"));
		});
		
		return new DelegatingOAuth2TokenGenerator(
				jwt, 
				new OAuth2AccessTokenGenerator(),
				new OAuth2RefreshTokenGenerator());
	}
}
