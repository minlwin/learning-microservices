package com.jdc.auth.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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

	private final ActivationRepo activationRepo;
	private final IdentityRepo identityRepo;
	private final ProfileRepo profileRepo;
	private final PasswordEncoder passwordEncoder;
	private final OtpCodeService otpService;

	@Value("${app.otp.expiration.activation}")
	private int otpExpiration;
	@Value("${app.otp.cooldown.missing-count}")
	private int maxFailures;
	@Value("${app.otp.cooldown.waiting-duration}")
	private int cooldownMinutes;
	@Value("${app.otp.resend.interval-seconds:60}")
	private int resendIntervalSeconds;

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
		if(!otp.getExpireAt().isAfter(LocalDateTime.now())) {
			throw new BusinessRuleViolationException("Your verification code has expired. Please request a new code.");
		}
		otp.setCheckedAt(LocalDateTime.now());
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
		if(profileRepo.existsByIdentityAndSystem(identity, Profile.System.Management)) {
			throw new BusinessRuleViolationException("A management account already exists for this email address.");
		}
		var profile = new Profile();
		profile.setIdentity(identity);
		profile.setSystem(Profile.System.Management);
		profile.setRole(Profile.Role.Management);
		profile.setStatus(Profile.Status.Active);
		profile.setPassword(passwordEncoder.encode(form.password()));
		profile = profileRepo.save(profile);
		activation.setInitializedAt(LocalDateTime.now());
		return new InitializationResult(profile.getId());
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
		var now = LocalDateTime.now();
		if(otp.getSendAt() != null && otp.getSendAt().plusSeconds(resendIntervalSeconds).isAfter(now)) {
			throw new BusinessRuleViolationException("Please wait until %s before requesting another code."
					.formatted(otp.getSendAt().plusSeconds(resendIntervalSeconds)));
		}
		var code = otpService.sendForActivation(activation.getEmail(), otpExpiration);
		otp.setOtpCode(passwordEncoder.encode(code));
		otp.setStatus(CheckStatus.Send);
		otp.setSendCount(otp.getSendCount() + 1);
		otp.setSendAt(now);
		otp.setExpireAt(now.plusMinutes(otpExpiration));
	}

	private void checkCooldown(OtpRecord otp) {
		if(otp.getFailCount() >= maxFailures) {
			var retryAt = otp.getCheckedAt().plusMinutes(cooldownMinutes);
			if(retryAt.isAfter(LocalDateTime.now())) {
				throw new BusinessRuleViolationException("You have reached the failed verification attempt limit. Please try again after %s.".formatted(retryAt));
			}
			otp.setFailCount(0);
		}
	}
}
