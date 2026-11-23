package com.jdc.auth.service;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import com.jdc.auth.props.AuthenticationProps;
import com.jdc.auth.props.OtpCodeProps;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jdc.auth.api.input.InitializationForm;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.api.output.InitializationResult;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.ResourceNotFoundException;
import com.jdc.auth.exceptions.OtpVerificationException;
import com.jdc.auth.model.entity.Activation;
import com.jdc.auth.model.entity.Identity;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.dto.OtpRecord;
import com.jdc.auth.model.entity.dto.OtpRecord.CheckStatus;
import com.jdc.auth.model.repo.ActivationRepo;
import com.jdc.auth.model.repo.IdentityRepo;
import com.jdc.auth.model.repo.ProfileRepo;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ActivationService {
	private final AuthenticationProps properties;
	private final OtpCodeProps otpProperties;



	private final ActivationRepo activationRepo;
	private final IdentityRepo identityRepo;
	private final ProfileRepo profileRepo;
	private final PasswordEncoder passwordEncoder;
	private final OtpCodeService otpService;


	/** Sends the first code; repeated requests follow the same resend restrictions. */
	public void request(UUID id) {
		sendCode(findAvailable(id));
	}

	public void resendOtp(UUID id) {
		var activation = findAvailable(id);
		if(activation.getOtpRecord() == null || activation.getOtpRecord().getSendAt() == null) {
			throw new BusinessRuleViolationException("Please request an activation verification code first.");
		}
		sendCode(activation);
	}

	@Transactional(dontRollbackOn = OtpVerificationException.class)
	public void validateOtp(UUID id, OtpForm form) {
		var activation = findAvailable(id);
		var otp = activation.getOtpRecord();
		if(otp == null || otp.getSendAt() == null) {
			throw new BusinessRuleViolationException("Please request an activation verification code first.");
		}
		if(otp.getStatus() == CheckStatus.Success) {
			throw new BusinessRuleViolationException("Your email address has already been verified. Please continue with account initialization.");
		}
		checkCooldown(otp);
		if(!otp.getExpireAt().isAfter(Instant.now())) {
			throw new BusinessRuleViolationException("Your verification code has expired. Please request a new code.");
		}
		otp.setCheckedAt(Instant.now());
		if(!passwordEncoder.matches(form.otpCode(), otp.getOtpCode())) {
			otp.setStatus(CheckStatus.Fail);
			otp.setFailCount(otp.getFailCount() + 1);
			throw new OtpVerificationException("The verification code is incorrect.");
		}
		otp.setStatus(CheckStatus.Success);
	}

	public InitializationResult initialize(UUID id, InitializationForm form) {
		var activation = findAvailable(id);
		if(activation.getOtpRecord() == null || activation.getOtpRecord().getStatus() != CheckStatus.Success) {
			throw new BusinessRuleViolationException("Please verify your email address before setting your account password.");
		}
		var identity = identityRepo.findOneByUsername(activation.getEmail()).orElseGet(() -> {
			var created = new Identity();
			created.setUsername(activation.getEmail());
			return identityRepo.save(created);
		});
		if(profileRepo.existsByIdentityAndSystem(identity, properties.getActivation().getSystem())) {
			throw new BusinessRuleViolationException("A management account already exists for this email address.");
		}
		var profile = new Profile();
		profile.setIdentity(identity);
		profile.setSystem(properties.getActivation().getSystem());
		profile.setRole(properties.getActivation().getRole());
		profile.setStatus(Profile.Status.Active);
		profile.setPassword(passwordEncoder.encode(form.password()));
		profile = profileRepo.save(profile);
		activation.setInitializedAt(Instant.now());
		return new InitializationResult(profile.getId(), null, 0);
	}

	private Activation findAvailable(UUID id) {
		var activation = activationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No activation request was found for the provided ID."));
		if(activation.getInitializedAt() != null) {
			throw new BusinessRuleViolationException("This activation request has already been used to initialize an account.");
		}
		return activation;
	}

	private void sendCode(Activation activation) {
		var otp = activation.getOtpRecord();
		if(otp == null) {
			otp = new OtpRecord();
			activation.setOtpRecord(otp);
		}
		if(otp.getStatus() == CheckStatus.Success) {
			throw new BusinessRuleViolationException("Your email address has already been verified. Please continue with account initialization.");
		}
		checkCooldown(otp);
		var now = Instant.now();
		if(otp.getSendAt() != null && otp.getSendAt().plusSeconds(otpProperties.getResend().getIntervalSeconds()).isAfter(now)) {
			throw new BusinessRuleViolationException("Please wait until %s before requesting another code."
					.formatted(otp.getSendAt().plusSeconds(otpProperties.getResend().getIntervalSeconds())));
		}
		var code = otpService.sendForActivation(activation.getEmail(), otpProperties.getExpiration().getActivation());
		otp.setOtpCode(passwordEncoder.encode(code));
		otp.setStatus(CheckStatus.Send);
		otp.setSendCount(otp.getSendCount() + 1);
		otp.setSendAt(now);
		otp.setExpireAt(now.plus(Duration.ofMinutes(otpProperties.getExpiration().getActivation())));
	}

	private void checkCooldown(OtpRecord otp) {
		if(otp.getFailCount() >= otpProperties.getCooldown().getMissingCount()) {
			var retryAt = otp.getCheckedAt().plus(Duration.ofMinutes(otpProperties.getCooldown().getWaitingDuration()));
			if(retryAt.isAfter(Instant.now())) {
				throw new BusinessRuleViolationException("You have reached the failed verification attempt limit. Please try again after %s.".formatted(retryAt));
			}
			otp.setFailCount(0);
		}
	}
}
