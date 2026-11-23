package com.jdc.auth.api.secured;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;
import com.jdc.auth.service.LogoutService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logout")
public class LogoutApi {
	private final LogoutService service;

	/** Logs out every device for the authenticated profile. No user ID is accepted from the request. */
	@PostMapping
	public void logout(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
		Number version = principal.getAttribute("token_version");
		service.logout(UUID.fromString(principal.getName()), version.longValue());
	}
}
