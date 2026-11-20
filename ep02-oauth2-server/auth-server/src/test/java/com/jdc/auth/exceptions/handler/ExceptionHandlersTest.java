package com.jdc.auth.exceptions.handler;

import org.hamcrest.Matchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.exceptions.*;
import jakarta.validation.Valid;

class ExceptionHandlersTest {
	@RestController
	static class TestApi {
		@GetMapping("/failure/{kind}")
		void fail(@PathVariable String kind) {
			switch(kind) {
				case "auth" -> throw new BadCredentialsException("private authentication reason");
				case "denied" -> throw new AccessDeniedException("private permission reason");
				case "missing" -> throw new ResourceNotFoundException("No registration was found.");
				case "business" -> throw new BusinessRuleViolationException("Please verify your email address.");
				case "otp" -> throw new OtpVerificationException("The verification code is incorrect.");
				case "conflict" -> throw new DataIntegrityViolationException("private database detail");
				case "mail" -> throw new MailSendException("private SMTP detail");
				default -> throw new IllegalStateException("private server detail");
			}
		}
		@PostMapping("/input/{id}")
		void input(@PathVariable UUID id, @Valid @RequestBody OtpForm form) { }
	}
	private MockMvc mvc;
	@BeforeEach void setup() {
		mvc = MockMvcBuilders.standaloneSetup(new TestApi()).setControllerAdvice(new ExceptionHandlers()).build();
	}
	@Test void mapsApplicationExceptionsWithoutLeakingInternalDetails() throws Exception {
		String[] kinds = {"auth", "denied", "missing", "business", "otp", "conflict", "mail", "unexpected"};
		int[] statuses = {401, 403, 404, 400, 400, 409, 503, 500};
		for(int i = 0; i < kinds.length; i++) {
			mvc.perform(get("/failure/" + kinds[i]))
					.andExpect(status().is(statuses[i]))
					.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
					.andExpect(jsonPath("$.status").value(statuses[i]))
					.andExpect(jsonPath("$.instance").value("/failure/" + kinds[i]))
					.andExpect(header().string("Cache-Control", "no-store"))
					.andExpect(content().string(Matchers.not(Matchers.containsString("private"))));
		}
	}
	@Test void handlesFieldValidationMalformedJsonAndInvalidUuid() throws Exception {
		var url = "/input/" + UUID.randomUUID();
		mvc.perform(post(url).contentType("application/json").content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("otpCode"));
		mvc.perform(post(url).contentType("application/json").content("{"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
		mvc.perform(post("/input/not-a-uuid").contentType("application/json").content("{\"otpCode\":\"123456\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
	}
	@Test void preservesFrameworkStatusesAndHeaders() throws Exception {
		mvc.perform(get("/input/" + UUID.randomUUID()))
				.andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"))
				.andExpect(jsonPath("$.status").value(405));
		mvc.perform(post("/input/" + UUID.randomUUID()).contentType("text/plain").content("hello"))
				.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
	}
}
