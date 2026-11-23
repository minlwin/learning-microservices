package com.jdc.auth.api.publicapi;

import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.auth.api.input.InitializationForm;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.api.output.InitializationResult;
import com.jdc.auth.service.ActivationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/activate/{id}")
public class ActivationApi {

	private final ActivationService service;

	/**
	 * Requests an email verification code for management account activation.
	 *
	 * <p>
	 * When a management user is created, Management Service publishes an invitation
	 * event through its outbox. Auth Server creates an expiring activation record
	 * and queues an activation link email.
	 * </p>
	 *
	 * <p>
	 * The link contains the activation ID. The client calls this endpoint to
	 * request a verification code for the email address in the activation record.
	 * </p>
	 *
	 * @param id the activation ID from the activation link
	 */
	@PostMapping
	public void request(@PathVariable UUID id) {
		service.request(id);
	}

	/**
	 * Verifies the email verification code for the activation request.
	 *
	 * <p>
	 * Successful verification allows the user to set their account password.
	 * </p>
	 *
	 * @param id   the activation ID
	 * @param form the verification code submitted by the user
	 */
	@PostMapping("/otp/validate")
	public void validate(@PathVariable UUID id, @RequestBody @Validated OtpForm form) {
		service.validateOtp(id, form);
	}

	/**
	 * Requests a new email verification code for the activation request.
	 *
	 * <p>
	 * The new code replaces the previous code and starts a new expiration period.
	 * Resend restrictions and failed verification cooldowns are enforced.
	 * </p>
	 *
	 * @param id the activation ID
	 */
	@PostMapping("/otp/resend")
	public void resend(@PathVariable UUID id) {
		service.resendOtp(id);
	}

	/**
	 * Sets the password and completes management account initialization.
	 *
	 * <p>
	 * The email address must be verified before initialization. The activation
	 * request must be consumed after success to prevent repeated initialization.
	 * </p>
	 *
	 * @param id   the activation ID
	 * @param form the initial account password
	 * @return the ID of the initialized management profile
	 */
	@PostMapping("/initialize")
	public InitializationResult initialize(@PathVariable UUID id, @RequestBody @Validated InitializationForm form) {
		return service.initialize(id, form);
	}

}
