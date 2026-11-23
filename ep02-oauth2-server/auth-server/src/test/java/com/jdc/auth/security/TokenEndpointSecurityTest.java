package com.jdc.auth.security;

import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.mock.web.MockServletContext;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.auth.AuthTestProperties;
import com.jdc.auth.AuthorizationServerConfig;
import com.jdc.auth.JdbcTestSupport;
import com.jdc.auth.SecurityConfig;
import com.jdc.auth.SessionSecurityConfig;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.Identity;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.model.repo.IdentityRepo;
import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.api.secured.LogoutApi;
import com.jdc.auth.api.secured.EmailCheckApi;
import com.jdc.auth.api.secured.TokenIntrospectionApi;
import com.jdc.auth.exceptions.handler.ExceptionHandlers;
import com.jdc.auth.service.EmailCheckService;
import com.jdc.auth.service.LoginTicketService;
import com.jdc.auth.service.LogoutService;
import com.jdc.auth.service.TokenValidationService;

class TokenEndpointSecurityTest {

	@Configuration
	@EnableWebSecurity
	@EnableWebMvc
	static class Config {
		@Bean
		JdbcOperations jdbc() {
			return JdbcTestSupport.createDatabase();
		}

		@Bean
		ProfileRepo profiles() {
			return mock(ProfileRepo.class);
		}

		@Bean
		IdentityRepo identities() {
			return mock(IdentityRepo.class);
		}

		@Bean
		LoginTicketService tickets() {
			return mock(LoginTicketService.class);
		}

		@Bean
		AuthenticationProps properties() {
			var properties = AuthTestProperties.create();
			var systems = new HashMap<>(properties.getSystems());
			systems.put("Merchant", AuthTestProperties.system("MerchantOperator"));
			properties.setSystems(systems);
			var merchant = client("Merchant");
			merchant.setScopes(Set.of("orders.read", "payments.write"));
			merchant.setAudiences(List.of("orders.api", "payments.api"));
			var consumer = client("Consumer");
			consumer.setScopes(Set.of("consumer.api", "consumer.onboarding"));
			properties.setClients(Map.of("consumer-demo", consumer, "management-demo", client("Management"),
					"merchant-demo", merchant));
			properties.setResources(Map.of("consumer-service",
					AuthTestProperties.resource("consumer-resource-secret", "consumer.api", "management.api"),
					"management-service", AuthTestProperties.resource("management-resource-secret", "management.api"),
					"orders-service", AuthTestProperties.resource("orders-secret", "orders.api")));
			properties.getResources().get("consumer-service").setEmailCheckSystems(Set.of("Consumer"));
			return properties;
		}

		@Bean
		TestApi api() {
			return new TestApi();
		}
	}

	@RestController
	static class TestApi {
		@GetMapping("/api/ping")
		String ping() {
			return "ok";
		}
	}

	private AnnotationConfigWebApplicationContext context;
	private MockMvc mvc;
	private Profile profile;
	private LoginTicketService tickets;

	@BeforeEach
	void setup() {
		context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.register(Config.class, SecurityConfig.class, AuthorizationServerConfig.class, ExceptionHandlers.class,
				SessionSecurityConfig.class, TokenValidationService.class, LogoutService.class, LogoutApi.class,
				TokenIntrospectionApi.class, EmailCheckService.class, EmailCheckApi.class);
		context.refresh();
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
		profile = new Profile();
		var identity = new Identity();
		identity.setUsername("alice@example.com");
		profile.setIdentity(identity);
		profile.setId(UUID.randomUUID());
		profile.setSystem("Consumer");
		profile.setRole("Consumer");
		profile.setStatus(Profile.Status.Active);
		when(context.getBean(ProfileRepo.class).findById(profile.getId())).thenReturn(Optional.of(profile));
		tickets = context.getBean(LoginTicketService.class);
		var used = new AtomicBoolean();
		when(tickets.consume("ticket", "consumer-demo")).thenAnswer(_ -> {
			if (used.getAndSet(true)) {
				throw new BadCredentialsException("Already used");
			}
			return new LoginTicketService.AuthenticatedLogin(profile.getId(), profile.getTokenVersion(), false);
		});
	}

	@AfterEach
	void close() {
		context.close();
	}

	@Test
	void deniesUnlistedApiRoutesAndBlocksEveryOtherProtocolEndpointWithoutRedirects() throws Exception {
		mvc.perform(get("/api/ping")).andExpect(status().isForbidden());
		mvc.perform(post("/api/ping")).andExpect(status().isForbidden());
		String[] paths = { "/", "/login", "/logout", "/oauth2/authorize", "/oauth2/jwks", "/oauth2/introspect",
				"/oauth2/revoke", "/oauth2/device_authorization", "/oauth2/device_verification", "/oauth2/par",
				"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration", "/userinfo",
				"/api/ping", "/connect/logout", "/connect/register", "/oauth2/register", "/oauth2/token" };
		for (String path : paths) {
			mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Location"));
			if (!path.equals("/oauth2/token")) {
				mvc.perform(post(path).header("Authorization", basic("consumer-demo", "consumer-demo-secret")))
						.andExpect(status().isForbidden()).andExpect(header().doesNotExist("Location"));
			}
		}
	}

	@Test
	void exchangesTicketRotatesRefreshAndRejectsReplayAndInactiveProfiles() throws Exception {
		var result = mvc
				.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
						.param("grant_type", LoginTicketGrantToken.GRANT_TYPE.getValue())
						.param("login_ticket", "ticket"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty())
				.andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
		String refresh = JsonPath.read(result.getResponse().getContentAsString(), "$.refresh_token");
		var refreshed = mvc
				.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
						.param("grant_type", "refresh_token").param("refresh_token", refresh))
				.andExpect(status().isOk()).andReturn();
		String next = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.refresh_token");
		assertNotEquals(refresh, next);
		mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
				.param("grant_type", "refresh_token").param("refresh_token", refresh))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
		profile.setStatus(Profile.Status.Locked);
		mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
				.param("grant_type", "refresh_token").param("refresh_token", next)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
		mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
				.param("grant_type", LoginTicketGrantToken.GRANT_TYPE.getValue()).param("login_ticket", "ticket"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void exchangesPendingProfileTicketForRestrictedOnboardingTokenWithoutRefresh() throws Exception {
		profile.setStatus(Profile.Status.Pending);
		when(tickets.consume("onboarding", "consumer-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(profile.getId(), 0, true));

		var response = mvc
				.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
						.param("grant_type", LoginTicketGrantToken.GRANT_TYPE.getValue())
						.param("login_ticket", "onboarding"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("consumer.onboarding"))
				.andExpect(jsonPath("$.refresh_token").doesNotExist()).andReturn();
		String accessToken = JsonPath.read(response.getResponse().getContentAsString(), "$.access_token");
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.param("token", accessToken)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.scope").value("consumer.onboarding"));

		profile.setStatus(Profile.Status.Active);
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.param("token", accessToken)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void requiresClientAuthenticationAndRejectsUnsupportedGrantTypes() throws Exception {
		mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "wrong"))
				.param("grant_type", LoginTicketGrantToken.GRANT_TYPE.getValue()).param("login_ticket", "ticket"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));
		verifyNoInteractions(tickets);
		mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
				.param("grant_type", "password")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unsupported_grant_type"));
	}

	@Test
	void logoutRevokesEveryDeviceButPreservesOtherProfilesAndAllowsFreshLogin() throws Exception {
		var profiles = context.getBean(ProfileRepo.class);
		when(profiles.revokeTokens(eq(profile.getId()), anyLong())).thenAnswer(invocation -> {
			long expected = invocation.getArgument(1);
			if (profile.getTokenVersion() != expected) {
				return 0;
			}
			profile.setTokenVersion(expected + 1);
			return 1;
		});
		when(tickets.consume("second", "consumer-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(profile.getId(), 0, false));
		var first = exchange("consumer-demo", "consumer-demo-secret", "ticket");
		var second = exchange("consumer-demo", "consumer-demo-secret", "second");
		var management = new Profile();
		management.setId(UUID.randomUUID());
		management.setSystem("Management");
		management.setRole("Management");
		management.setStatus(Profile.Status.Active);
		when(profiles.findById(management.getId())).thenReturn(Optional.of(management));
		when(tickets.consume("management", "management-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(management.getId(), 0, false));
		var other = exchange("management-demo", "management-demo-secret", "management");
		checkActive(second.get("access_token"), true);
		mvc.perform(post("/api/logout").header("Authorization", "Bearer " + first.get("access_token")))
				.andExpect(status().isOk());
		assertEquals(1, profile.getTokenVersion());
		checkActive(first.get("access_token"), false);
		checkActive(second.get("access_token"), false);
		checkActive(other.get("access_token"), true);
		for (var token : List.of(first, second)) {
			mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
					.param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token")))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
		}
		when(tickets.consume("fresh", "consumer-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(profile.getId(), 1, false));
		var fresh = exchange("consumer-demo", "consumer-demo-secret", "fresh");
		checkActive(fresh.get("access_token"), true);
		when(tickets.consume("stale", "consumer-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(profile.getId(), 0, false));
		mvc.perform(post("/oauth2/token").header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
				.param("grant_type", LoginTicketGrantToken.GRANT_TYPE.getValue()).param("login_ticket", "stale"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void requiresBearerForLogoutAndSeparateResourceCredentialsForIntrospection() throws Exception {
		mvc.perform(post("/api/logout")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/logout").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
		var token = exchange("consumer-demo", "consumer-demo-secret", "ticket");
		mvc.perform(post("/api/logout").header("Authorization", "Bearer " + token.get("refresh_token")))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", "Bearer " + token.get("access_token"))
				.param("token", token.get("access_token"))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("consumer-service", "wrong")).param("token", token.get("access_token")))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("management-service", "management-resource-secret"))
				.param("token", token.get("access_token"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void administratorReceivesManagementAccessAndAdministratorRoleIncludingOnRefresh() throws Exception {
		profile.setSystem("Management");
		profile.setRole("Administrator");
		when(tickets.consume("administrator", "management-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(profile.getId(), 0, false));
		var token = exchange("management-demo", "management-demo-secret", "administrator");
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("management-service", "management-resource-secret"))
				.param("token", token.get("access_token"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.roles[0]").value("Administrator"))
				.andExpect(jsonPath("$.username").value("alice@example.com"))
				.andExpect(jsonPath("$.system").value("Management"))
				.andExpect(jsonPath("$.aud[0]").value("management.api"));
		mvc.perform(post("/oauth2/token").header("Authorization", basic("management-demo", "management-demo-secret"))
				.param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("management.api"));
	}

	@Test
	void readsIssuedTokensThroughNewRepositoryAndServiceInstances() throws Exception {
		var tokens = exchange("consumer-demo", "consumer-demo-secret", "ticket");
		var jdbc = context.getBean(JdbcOperations.class);
		var clients = new JdbcRegisteredClientRepository(jdbc);
		var restored = new AuthorizationServerConfig().authorizationService(jdbc, clients);
		var authorization = restored.findByToken(tokens.get("access_token"), OAuth2TokenType.ACCESS_TOKEN);
		assertNotNull(authorization);
		assertEquals(profile.getId().toString(), authorization.getPrincipalName());
		assertEquals(0L, ((Number) authorization.getAccessToken().getClaims().get("token_version")).longValue());
		assertNotNull(restored.findByToken(tokens.get("refresh_token"), OAuth2TokenType.REFRESH_TOKEN));
	}

	@Test
	void issuesRefreshesAndIntrospectsTokensForConfiguredSystemAndCustomRole() throws Exception {
		profile.setSystem("Merchant");
		profile.setRole("MerchantOperator");
		when(tickets.consume("merchant-ticket", "merchant-demo"))
				.thenReturn(new LoginTicketService.AuthenticatedLogin(profile.getId(), 0, false));
		var tokens = exchange("merchant-demo", "management-demo-secret", "merchant-ticket");
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("orders-service", "orders-secret"))
				.param("token", tokens.get("access_token"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.system").value("Merchant"))
				.andExpect(jsonPath("$.roles[0]").value("MerchantOperator"))
				.andExpect(jsonPath("$.aud", Matchers.containsInAnyOrder("orders.api", "payments.api")));
		mvc.perform(post("/oauth2/token").header("Authorization", basic("merchant-demo", "management-demo-secret"))
				.param("grant_type", "refresh_token").param("refresh_token", tokens.get("refresh_token")))
				.andExpect(status().isOk());
		profile.setRole("UnconfiguredRole");
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("orders-service", "orders-secret"))
				.param("token", tokens.get("access_token"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void emailCheckRequiresServiceCredentialsAndExplicitSystemPermission() throws Exception {
		String uri = "/api/internal/profiles/email/check";
		String body = "{\"system\":\"Consumer\",\"email\":\"alice@example.com\"}";
		mvc.perform(post(uri).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
		mvc.perform(
				post(uri).header("Authorization", "Bearer user-token").contentType("application/json").content(body))
				.andExpect(status().isUnauthorized());
		mvc.perform(post(uri).header("Authorization", basic("consumer-demo", "consumer-demo-secret"))
				.contentType("application/json").content(body)).andExpect(status().isUnauthorized());
		mvc.perform(post(uri).header("Authorization", basic("management-service", "management-resource-secret"))
				.contentType("application/json").content(body)).andExpect(status().isForbidden());
		mvc.perform(post(uri).header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.contentType("application/json").content(body.replace("Consumer", "Management")))
				.andExpect(status().isForbidden());
		verify(context.getBean(ProfileRepo.class), never()).existsByIdentityUsernameAndSystem(anyString(), anyString());
	}

	@Test
	void emailCheckReturnsSameSystemExistenceAndRejectsInvalidInput() throws Exception {
		String uri = "/api/internal/profiles/email/check";
		String body = "{\"system\":\"Consumer\",\"email\":\"alice@example.com\"}";
		var profiles = context.getBean(ProfileRepo.class);
		var identities = context.getBean(IdentityRepo.class);
		when(identities.findOneByUsername("alice@example.com")).thenReturn(Optional.of(profile.getIdentity()))
				.thenReturn(Optional.empty());
		when(profiles.findByIdentityUsernameAndSystem("alice@example.com", "Consumer"))
				.thenReturn(Optional.of(profile)).thenReturn(Optional.empty());
		mvc.perform(post(uri).header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.contentType("application/json").content(body)).andExpect(status().isOk())
				.andExpect(jsonPath("$.identityExists").value(true)).andExpect(jsonPath("$.profileExists").value(true))
				.andExpect(jsonPath("$.profileStatus").value("Active"))
				.andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(post(uri).header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.contentType("application/json").content(body)).andExpect(status().isOk())
				.andExpect(jsonPath("$.identityExists").value(false))
				.andExpect(jsonPath("$.profileExists").value(false))
				.andExpect(jsonPath("$.profileStatus").doesNotExist());
		mvc.perform(post(uri).header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.contentType("application/json").content(body.replace("alice@example.com", "invalid")))
				.andExpect(status().isBadRequest());
		mvc.perform(post(uri).header("Authorization", basic("consumer-service", "consumer-resource-secret"))
				.contentType("application/json").content(body.replace("Consumer", "")))
				.andExpect(status().isBadRequest());
		verify(identities, times(2)).findOneByUsername("alice@example.com");
		verify(profiles, times(2)).findByIdentityUsernameAndSystem("alice@example.com", "Consumer");
	}

	private Map<String, String> exchange(String client, String secret, String ticket) throws Exception {
		var response = mvc
				.perform(post("/oauth2/token").header("Authorization", basic(client, secret))
						.param("grant_type", LoginTicketGrantToken.GRANT_TYPE.getValue()).param("login_ticket", ticket))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return Map.of("access_token", JsonPath.read(response, "$.access_token"), "refresh_token",
				JsonPath.read(response, "$.refresh_token"));
	}

	private void checkActive(String token, boolean active) throws Exception {
		mvc.perform(post("/api/internal/tokens/introspect").contentType("application/x-www-form-urlencoded")
				.header("Authorization", basic("consumer-service", "consumer-resource-secret")).param("token", token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.active").value(active));
	}

	private String basic(String id, String secret) {
		return "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
	}

	private static AuthenticationProps.Client client(String system) {
		var client = new AuthenticationProps.Client();
		client.setSystem(system);
		client.setScopes(Set.of(system.toLowerCase(Locale.ROOT) + ".api"));
		client.setAudiences(List.of(system.toLowerCase(Locale.ROOT) + ".api"));
		client.setSecret("Consumer".equals(system) ? "consumer-demo-secret" : "management-demo-secret");
		return client;
	}
}
