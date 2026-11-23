package com.jdc.auth;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.core.env.Environment;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
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
	SecurityFilterChain tokenEndpointSecurity(
			HttpSecurity http,
			LoginTicketService tickets,
			OAuth2AuthorizationService authorizations,
			OAuth2TokenGenerator<?> generator,
			ExceptionHandlers handlers) {

		// Restrict the entire chain, not just authorization rules: protocol filters
		// can otherwise serve metadata, keys, and other endpoints before authorization.
		var tokenEndpointMatcher = PathPatternRequestMatcher.withDefaults()
				.matcher(HttpMethod.POST, "/oauth2/token");

		http.securityMatcher(tokenEndpointMatcher);

		http.with(new OAuth2AuthorizationServerConfigurer(), server -> {

			server.clientAuthentication(client -> client.errorResponseHandler(handlers::oauth2Failure));

			server.tokenEndpoint(endpoint -> {
				endpoint.errorResponseHandler(handlers::oauth2Failure);
				endpoint.accessTokenRequestConverter(new LoginTicketGrantConverter());
				endpoint.authenticationProvider(new LoginTicketGrantProvider(tickets, authorizations, generator));
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
	RegisteredClientRepository registeredClients(AuthenticationProps properties, PasswordEncoder encoder, JdbcOperations jdbc) {
		properties.validateClients();
		var clients = new JdbcRegisteredClientRepository(jdbc);

		for (var entry : properties.getClients().entrySet()) {
			var clientId = entry.getKey();
			var definition = entry.getValue();
			if (definition.getSystem() == null) {
				throw new IllegalStateException("Missing profile system for " + clientId);
			}
			var system = definition.getSystem();
			var secret = definition.getSecret();
			if (secret == null || secret.isBlank()) {
				throw new IllegalStateException("Missing client secret for " + clientId);
			}

			var clientSettings = ClientSettings.builder();
			clientSettings.setting("app.system", system);
			clientSettings.setting("app.audiences", new ArrayList<>(definition.getAudiences()));

			var tokenSettings = TokenSettings.builder();
			tokenSettings.accessTokenTimeToLive(properties.getAccessTokenTimeToLive());
			tokenSettings.refreshTokenTimeToLive(properties.getRefreshTokenTimeToLive());
			tokenSettings.reuseRefreshTokens(false);

			var existing = clients.findByClientId(clientId);
			var client = RegisteredClient.withId(existing == null ? clientId : existing.getId());
			client.clientId(clientId);
			var encodedSecret = existing == null ? null : existing.getClientSecret();
			if (encodedSecret == null || !encoder.matches(secret, encodedSecret)) {
				encodedSecret = encoder.encode(secret);
			}
			client.clientSecret(encodedSecret);
			client.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
			client.authorizationGrantType(LoginTicketGrantToken.GRANT_TYPE);
			client.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
			client.scopes(scopes -> scopes.addAll(definition.getScopes()));
			client.clientSettings(clientSettings.build());
			client.tokenSettings(tokenSettings.build());

			clients.save(client.build());
		}
		return clients;
	}

	@Bean
	public OAuth2AuthorizationService authorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
		// Allow Long token versions, immutable collections used by JWT claims, and the
		// singleton immutable set used for the restricted onboarding scope.
		var allowedTypes = BasicPolymorphicTypeValidator.builder();
		allowedTypes.allowIfSubType(Long.class);
		allowedTypes.allowIfSubType(List.of("role").getClass());
		allowedTypes.allowIfSubType(List.of().getClass());
		allowedTypes.allowIfSubType(Set.of("consumer.onboarding").getClass());
		var mapperBuilder = JsonMapper.builder();
		mapperBuilder.addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), allowedTypes));
		var mapper = mapperBuilder.build();
		var service = new JdbcOAuth2AuthorizationService(jdbc, clients);
		service.setAuthorizationRowMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients, mapper));
		service.setAuthorizationParametersMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(mapper));
		return service;
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(AuthenticationProps properties) {
		var settings = AuthorizationServerSettings.builder();
		settings.issuer(properties.getIssuer());
		return settings.build();
	}

	@Bean
	JWKSource<SecurityContext> signingKeys(AuthenticationProps properties, Environment environment) throws Exception {
		var privatePath = properties.getSigningKeys().getPrivateKeyPath();
		var publicPath = properties.getSigningKeys().getPublicKeyPath();
		if (privatePath.toAbsolutePath().normalize().equals(publicPath.toAbsolutePath().normalize())) {
			throw new IllegalStateException("Auth Server signing private and public keys must use separate files.");
		}
		if (Files.exists(privatePath) != Files.exists(publicPath)) {
			throw new IllegalStateException("Both Auth Server signing key files must exist together.");
		}
		if (!Files.exists(privatePath)) {
			if (environment.matchesProfiles("prod", "production")) {
				throw new IllegalStateException("Production Auth Server signing keys must be provisioned before startup.");
			}
			generateDevelopmentSigningKeys(privatePath, publicPath);
		}
		var keyFactory = KeyFactory.getInstance("RSA");
		var privateBytes = decodePem(Files.readString(privatePath), "PRIVATE KEY");
		var publicBytes = decodePem(Files.readString(publicPath), "PUBLIC KEY");
		var privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
		var publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
		if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)
				|| !(publicKey instanceof RSAPublicKey rsaPublicKey)) {
			throw new IllegalStateException("Auth Server signing keys must be RSA keys.");
		}
		if (!rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus())) {
			throw new IllegalStateException("Auth Server signing public and private keys do not match.");
		}
		var keyBuilder = new RSAKey.Builder(rsaPublicKey);
		keyBuilder.privateKey(rsaPrivateKey);
		var thumbprint = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
		keyBuilder.keyID(Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint));
		var key = keyBuilder.build();
		var keys = new JWKSet(key);
		return (selector, _) -> selector.select(keys);
	}

	private void generateDevelopmentSigningKeys(Path privatePath, Path publicPath) throws Exception {
		Files.createDirectories(privatePath.toAbsolutePath().getParent());
		Files.createDirectories(publicPath.toAbsolutePath().getParent());
		try {
			Files.createFile(privatePath, java.nio.file.attribute.PosixFilePermissions
					.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
		} catch (UnsupportedOperationException ignored) {
			Files.createFile(privatePath);
		}
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var pair = generator.generateKeyPair();
		var encoder = Base64.getMimeEncoder(64, new byte[] {'\n'});
		var privatePem = "-----BEGIN PRIVATE KEY-----\n" + encoder.encodeToString(pair.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		var publicPem = "-----BEGIN PUBLIC KEY-----\n" + encoder.encodeToString(pair.getPublic().getEncoded())
				+ "\n-----END PUBLIC KEY-----\n";
		Files.writeString(privatePath, privatePem, StandardCharsets.US_ASCII);
		Files.writeString(publicPath, publicPem, StandardCharsets.US_ASCII);
	}

	private byte[] decodePem(String pem, String keyType) {
		var body = pem.replace("-----BEGIN " + keyType + "-----", "")
				.replace("-----END " + keyType + "-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(body);
	}

	@Bean
	OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> keys, ProfileRepo profiles, AuthenticationProps properties) {
		var jwt = new JwtGenerator(new NimbusJwtEncoder(keys));

		jwt.setJwtCustomizer(context -> {
			var profile = profiles.findById(UUID.fromString(context.getPrincipal().getName()))
					.orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
			String system = context.getRegisteredClient().getClientSettings().getSetting("app.system");
			var onboardingGrant = context.getAuthorizationGrantType() != null
					&& LoginTicketGrantToken.GRANT_TYPE.equals(context.getAuthorizationGrantType())
					&& context.getAuthorizationGrant() instanceof LoginTicketGrantToken grant
					&& grant.isOnboarding();
			if ((!onboardingGrant && profile.getStatus() != Profile.Status.Active)
					|| (onboardingGrant && profile.getStatus() != Profile.Status.Pending)
					|| !ProfileAccess.allows(system, profile, properties)
					|| (onboardingGrant && !context.getAuthorizedScopes().equals(java.util.Set.of("consumer.onboarding")))) {
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
			claims.claim("system", profile.getSystem());
			claims.claim("roles", List.of(profile.getRole()));
			List<String> audiences = context.getRegisteredClient().getClientSettings().getSetting("app.audiences");
			claims.audience(audiences);
		});

		return new DelegatingOAuth2TokenGenerator(
				jwt,
				new OAuth2AccessTokenGenerator(),
				new OAuth2RefreshTokenGenerator());
	}
}
