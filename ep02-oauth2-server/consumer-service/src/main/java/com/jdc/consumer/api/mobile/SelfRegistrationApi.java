package com.jdc.consumer.api.mobile;

import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.consumer.api.mobile.input.SelfRegistrationForm;
import com.jdc.consumer.service.ConsumerAccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/mobile/registration")
public class SelfRegistrationApi {

	private final ConsumerAccountService service;

	@PostMapping
	public void register(@Validated @RequestBody SelfRegistrationForm form,
			@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
		if (!CurrentConsumer.username(principal).equals(form.email())) {
			throw new AccessDeniedException("You can register only your own email address.");
		}
		service.register(form);
	}
}
