package com.jdc.consumer.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jdc.consumer.api.management.ConsumerManagementApi;
import com.jdc.consumer.api.mobile.ProfileApi;
import com.jdc.consumer.api.mobile.SelfRegistrationApi;
import com.jdc.consumer.api.mobile.output.ConsumerProfile;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.exceptions.handler.ExceptionHandlers;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.service.ConsumerAccountService;

class ConsumerAccountApiTest {
	private final ConsumerAccountService service = mock(ConsumerAccountService.class);
	private MockMvc mvc;
	private static final String BODY = """
			{"name":"Alice","email":"alice@example.com","phone":"1234","dob":"1990-01-01","address":"Address"}
			""";

	@BeforeEach
	void setup() {
		var principal = new DefaultOAuth2AuthenticatedPrincipal(UUID.randomUUID().toString(), Map.of("username", "alice@example.com"), List.of());
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
		mvc = MockMvcBuilders.standaloneSetup(new ProfileApi(service), new SelfRegistrationApi(service), new ConsumerManagementApi(service))
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.setControllerAdvice(new ExceptionHandlers()).build();
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void usesTrustedUsernameInsteadOfAuthProfileUuid() throws Exception {
		var account = new Account();
		account.setEmail("alice@example.com");
		when(service.findByUsername("alice@example.com")).thenReturn(ConsumerProfile.from(account));
		mvc.perform(get("/api/mobile/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.com"));
		verify(service).findByUsername("alice@example.com");
	}

	@Test
	void acceptsValidDateOfBirthAndRejectsAnotherUsersEmail() throws Exception {
		mvc.perform(post("/api/mobile/registration").contentType("application/json").content(BODY)).andExpect(status().isOk());
		clearInvocations(service);
		mvc.perform(post("/api/mobile/registration").contentType("application/json")
				.content(BODY.replace("alice@example.com", "other@example.com"))).andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	@Test
	void rejectsInvalidInputAndReportsMissingAccount() throws Exception {
		mvc.perform(post("/api/management/consumer").contentType("application/json")
				.content(BODY.replace("1990-01-01", "2990-01-01"))).andExpect(status().isBadRequest());
		mvc.perform(post("/api/management/consumer").contentType("application/json")
				.content(BODY.replace("alice@example.com", "invalid-email"))).andExpect(status().isBadRequest());
		when(service.findByUsername("alice@example.com")).thenThrow(new ResourceNotFoundException("Missing account."));
		mvc.perform(get("/api/mobile/profile")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void delegatesApprovalAndValidatesUuidPath() throws Exception {
		var id = UUID.randomUUID();
		mvc.perform(put("/api/management/consumer/" + id + "/approve")).andExpect(status().isOk());
		verify(service).approve(id);
		mvc.perform(put("/api/management/consumer/not-a-uuid/approve")).andExpect(status().isBadRequest());
	}
}
