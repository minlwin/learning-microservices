package com.jdc.auth.api;

import com.jdc.auth.exceptions.handler.ExceptionHandlers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.jdc.auth.SecurityConfig;
import com.jdc.auth.service.AuthenticationService;
import com.jdc.auth.api.output.AuthenticationResult;

class AuthenticationApiTest {
	@Configuration
	@EnableWebSecurity
	@EnableWebMvc
	static class Config {
		@Bean AuthenticationService service() { return mock(AuthenticationService.class); }
		@Bean AuthenticationApi api(AuthenticationService service) { return new AuthenticationApi(service); }
	}
	@Test void acceptsAnonymousJsonLoginAndHandlesValidationAndCredentials() throws Exception {
		try(var context = new AnnotationConfigWebApplicationContext()) {
			context.setServletContext(new MockServletContext());
			context.register(Config.class, SecurityConfig.class, ExceptionHandlers.class);
			context.refresh();
			var service = context.getBean(AuthenticationService.class);
			when(service.authenticate(any())).thenReturn(new AuthenticationResult("ticket", 60));
			var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
			mvc.perform(get("/private"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
			String json = "{\"clientId\":\"consumer-demo\",\"username\":\"user@example.com\",\"password\":\"password\"}";
			mvc.perform(post("/api/auth").contentType("application/json").content(json))
					.andExpect(status().isOk()).andExpect(jsonPath("$.loginTicket").value("ticket"))
					.andExpect(jsonPath("$.expiresIn").value(60)).andExpect(header().string("Cache-Control", "no-store"));
			mvc.perform(post("/api/auth").contentType("application/json").content("{}"))
					.andExpect(status().isBadRequest());
			when(service.authenticate(any())).thenThrow(new BadCredentialsException("internal reason"));
			mvc.perform(post("/api/auth").contentType("application/json").content(json))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Authentication failed. Please check your credentials or sign in again."));
		}
	}
}
