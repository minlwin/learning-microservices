package com.jdc.consumer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.config.BeanPostProcessor;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.server.resource.introspection.*;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ResourceSecurityConfigTest {
	private static final String URI = "http://auth-server/api/internal/tokens/introspect";
	private static final String ACTIVE = "{\"active\":true,\"sub\":\"profile-id\",\"iss\":\"http://localhost:8080\",\"aud\":[\"consumer.api\"],\"scope\":\"consumer.api\"}";
	@Configuration
	@EnableWebSecurity
	@EnableWebMvc
	static class Config { @Bean TestApi api() { return new TestApi(); } }
	@RestController
	static class TestApi { @GetMapping("/api/self/check") String check() { return "ok"; } }

	@Test void checksRevocationOnEveryRequestAndFailsClosedWhenAuthServerIsDown() throws Exception {
		try(var context = new AnnotationConfigWebApplicationContext()) {
			context.setServletContext(new MockServletContext());
			TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
					"auth.introspection-uri=" + URI, "auth.resource-secret=secret");
			context.register(Config.class, ResourceSecurityConfig.class);
			var transportHolder = new AtomicReference<MockRestServiceServer>();
			context.addBeanFactoryPostProcessor(factory -> factory.addBeanPostProcessor(
					new BeanPostProcessor() {
						@Override
						public Object postProcessBeforeInitialization(Object bean, String name) {
							if ("introspectionHttpBuilder".equals(name) && bean instanceof RestClient.Builder builder) {
								transportHolder.set(MockRestServiceServer.bindTo(builder).build());
							}
							return bean;
						}
					}));
			context.refresh();
			var transport = transportHolder.get();
			transport.expect(requestTo(URI)).andExpect(header("Authorization", Matchers.startsWith("Basic ")))
					.andRespond(withSuccess(ACTIVE, MediaType.APPLICATION_JSON));
			transport.expect(requestTo(URI)).andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));
			transport.expect(requestTo(URI)).andRespond(withException(new IOException("Auth server unavailable")));
			var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
			mvc.perform(get("/api/self/check").header("Authorization", "Bearer token")).andExpect(status().isOk());
			mvc.perform(get("/api/self/check").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
			var introspector = context.getBean(OpaqueTokenIntrospector.class);
			assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect("token"));
			transport.verify();
		}
	}

	@Test void rejectsWrongIssuerOrAudience() {
		var config = new ResourceSecurityConfig();
		var builder = RestClient.builder();
		var transport = MockRestServiceServer.bindTo(builder).build();
		var http = builder.build();
		transport.expect(requestTo(URI)).andRespond(withSuccess(ACTIVE, MediaType.APPLICATION_JSON));
		transport.expect(requestTo(URI)).andRespond(withSuccess(ACTIVE, MediaType.APPLICATION_JSON));
		var wrongIssuer = config.liveTokenIntrospector(URI, http, "https://wrong-issuer", "consumer.api");
		assertThrows(BadOpaqueTokenException.class, () -> wrongIssuer.introspect("token"));
		var wrongAudience = config.liveTokenIntrospector(URI, http, "http://localhost:8080", "other.api");
		assertThrows(BadOpaqueTokenException.class, () -> wrongAudience.introspect("token"));
		transport.verify();
	}
	@Test void retainsConfiguredRoleFromTrustedIntrospectionResponse() {
		var builder = RestClient.builder();
		var transport = MockRestServiceServer.bindTo(builder).build();
		var http = builder.build();
		var json = ACTIVE.substring(0, ACTIVE.length() - 1) + ",\"roles\":[\"CustomOperator\"]}";
		transport.expect(requestTo(URI)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
		var introspector = new ResourceSecurityConfig().liveTokenIntrospector(URI, http, "http://localhost:8080", "consumer.api");
		var principal = introspector.introspect("token");
		assertTrue(principal.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_CustomOperator")));
		transport.verify();
	}

}
