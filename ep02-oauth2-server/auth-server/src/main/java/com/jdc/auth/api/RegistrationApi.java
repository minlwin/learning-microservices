package com.jdc.auth.api;

import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.auth.api.input.InitializationForm;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.api.input.RegistrationForm;
import com.jdc.auth.api.output.InitializationResult;
import com.jdc.auth.api.output.RegistrationResult;
import com.jdc.auth.service.RegistrationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/registration")
public class RegistrationApi {
	
	private final RegistrationService service;

	// Trusted subsystem duplication checks are handled separately by EmailCheckApi.

	/**
	 * Consumer self-registration
	 * 
	 * <ol>
	 * <li>Consumers register their email address for the consumer application.</li>
	 * <li>The authorization server sends a verification code to the provided email address.</li>
	 * </ol>
	 * 
	 * @param form the request data
	 * @return the registration ID
	 */
	@PostMapping
	public RegistrationResult register(@RequestBody @Validated RegistrationForm form) {
		return service.register(form);
	}
	
	/**
	 * OTP Verification
	 * 
	 * Consumers verify their email address before setting their account password.
	 * 
	 * @param id the registration ID
	 * @param form the request data
	 */
	@PostMapping("{id}/otp/validate")
	public void validate(@PathVariable UUID id, @RequestBody @Validated OtpForm form) {
		service.validateOtp(id, form);
	}
	
	/**
	 * Send again OTP
	 * 
	 * Consumers can request a new verification code.
	 * 
	 * @param id the registration ID
	 */
	@PostMapping("{id}/otp/resend")
	public void resend(@PathVariable UUID id) {
		service.resendOtp(id);
	}

	/**
	 * Consumer account initialization
	 * 
	 * After email verification, consumers set a password to create their account.
	 * 
	 * @param id the registration ID
	 * @param form the request data
	 * @return the ID of the created consumer profile
	 */
	@PostMapping("{id}/initialize")
	public InitializationResult initialize(@PathVariable UUID id, @RequestBody @Validated InitializationForm form) {
		return service.initialize(id, form);
	}
}
