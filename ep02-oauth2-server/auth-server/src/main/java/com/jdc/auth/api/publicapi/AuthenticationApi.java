package com.jdc.auth.api.publicapi;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.auth.api.input.AuthenticationForm;
import com.jdc.auth.api.output.AuthenticationResult;
import com.jdc.auth.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthenticationApi {

	private final AuthenticationService service;

	/**
	 * Checks the password for the profile system configured for the client ID.
	 *
	 * @param form the client ID, username, and password
	 * @return a short-lived login ticket and its lifetime in seconds
	 */
	@PostMapping
	public ResponseEntity<AuthenticationResult> authenticate(@Validated @RequestBody AuthenticationForm form) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.header("Pragma", "no-cache").body(service.authenticate(form));
	}
	
}
