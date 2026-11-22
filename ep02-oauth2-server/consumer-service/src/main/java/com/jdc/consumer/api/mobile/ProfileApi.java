package com.jdc.consumer.api.mobile;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.consumer.api.mobile.output.ConsumerProfile;
import com.jdc.consumer.service.ConsumerAccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/mobile/profile")
public class ProfileApi {
	
	private final ConsumerAccountService service;

	@GetMapping
	public ConsumerProfile getProfile(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
		return service.findByUsername(CurrentConsumer.username(principal));
	}
}
