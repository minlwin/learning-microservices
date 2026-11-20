package com.jdc.auth.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jdc.auth.api.input.InitializationForm;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.api.input.RegistrationForm;
import com.jdc.auth.api.output.InitializationResult;
import com.jdc.auth.api.output.RegistrationResult;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.ResourceNotFoundException;
import com.jdc.auth.exceptions.OtpVerificationException;
import com.jdc.auth.model.entity.Identity;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.Profile.Status;
import com.jdc.auth.model.entity.Profile.System;
import com.jdc.auth.model.entity.Registration;
import com.jdc.auth.model.entity.dto.OtpRecord;
import com.jdc.auth.model.entity.dto.OtpRecord.CheckStatus;
import com.jdc.auth.model.repo.IdentityRepo;
import com.jdc.auth.model.repo.ProfileRepo;
import com.jdc.auth.model.repo.RegistrationRepo;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RegistrationService {
	
	private static final String NO_DATA_MESSAGE = "No registration was found for the provided ID.";
	private final PasswordEncoder passwordEncoder;

	private final EmailValidationService emailValidationService;
	private final OtpCodeService otpService;
	
	private final RegistrationRepo registrationRepo;
	private final IdentityRepo identityRepo;
	private final ProfileRepo profileRepo;
	
	@Value("${app.otp.expiration.registration}")
	private int otpExpiration;
	
	@Value("${app.otp.cooldown.missing-count}")
	private int otpCooldownMissingCount;

	@Value("${app.otp.cooldown.waiting-duration}")
	private int otpCooldownWait;

	@Value("${app.otp.resend.interval-seconds:60}")
	private int resendIntervalSeconds;

	public RegistrationResult register(RegistrationForm form) {
		
		emailValidationService.validateForRegistration(form.email());
		
		var registration = new Registration();
		registration.setEmail(form.email());
		registration.setRegisteredAt(LocalDateTime.now());
		
		var otpCode = otpService.sendForRegistration(form.email());

		var otpRecord = new OtpRecord();
		otpRecord.setOtpCode(passwordEncoder.encode(otpCode));
		otpRecord.setStatus(CheckStatus.Send);
		otpRecord.setSendCount(1);
		otpRecord.setSendAt(LocalDateTime.now());
		otpRecord.setExpireAt(otpRecord.getSendAt().plusMinutes(otpExpiration));
		
		registration.setOtpRecord(otpRecord);
		registration = registrationRepo.save(registration);
		
		return new RegistrationResult(registration.getId());
	}

	@Transactional(dontRollbackOn = OtpVerificationException.class)
	public void validateOtp(UUID id, OtpForm form) {
		
		var registration = registrationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(NO_DATA_MESSAGE));
		
		var otpRecord = registration.getOtpRecord();

		if(otpRecord.getStatus() == CheckStatus.Success) {
			throw new BusinessRuleViolationException("Your email address has already been verified. Please continue with account initialization.");
		}
		
		if(!otpRecord.getExpireAt().isAfter(LocalDateTime.now())) {
			throw new BusinessRuleViolationException("Your verification code has expired. Please request a new code.");
		}
		
		checkCooldown(otpRecord);
		otpRecord.setCheckedAt(LocalDateTime.now());
		
		if(!passwordEncoder.matches(form.otpCode(), registration.getOtpRecord().getOtpCode())) {
			otpRecord.setStatus(CheckStatus.Fail);
			otpRecord.setFailCount(otpRecord.getFailCount() + 1);
			throw new OtpVerificationException("The verification code is incorrect.");
		}
		
		otpRecord.setStatus(CheckStatus.Success);
	}

	public void resendOtp(UUID id) {
		var registration = registrationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(NO_DATA_MESSAGE));
		
		var otpRecord = registration.getOtpRecord();

		if(otpRecord.getStatus() == CheckStatus.Success) {
			throw new BusinessRuleViolationException("Your email address has already been verified. You cannot request another verification code.");
		}
		
		checkCooldown(otpRecord);
		var resendAfter = otpRecord.getSendAt().plusSeconds(resendIntervalSeconds);
		if(resendAfter.isAfter(LocalDateTime.now())) {
			throw new BusinessRuleViolationException("Please wait until %s before requesting another code.".formatted(resendAfter));
		}

		// Send OTP
		var otpCode = otpService.sendForRegistration(registration.getEmail());
		
		otpRecord.setOtpCode(passwordEncoder.encode(otpCode));
		otpRecord.setStatus(CheckStatus.Send);
		otpRecord.setSendCount(otpRecord.getSendCount() + 1);
		otpRecord.setSendAt(LocalDateTime.now());
		otpRecord.setExpireAt(otpRecord.getSendAt().plusMinutes(otpExpiration));
	}

	public InitializationResult initialize(UUID id, InitializationForm form) {
		var registration = registrationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(NO_DATA_MESSAGE));
		
		if(registration.getInitializedAt() != null) {
			throw new BusinessRuleViolationException("This registration has already been used to create an account.");
		}

		if(registration.getOtpRecord().getStatus() != CheckStatus.Success) {
			throw new BusinessRuleViolationException("Please verify your email address before setting your account password.");
		}
		
		var identity = identityRepo.findOneByUsername(registration.getEmail()).orElseGet(() -> {
			var entity = new Identity();
			entity.setUsername(registration.getEmail());
			return identityRepo.save(entity);
		});
		
		if(profileRepo.existsByIdentityAndSystem(identity, System.Consumer)) {
			throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
		}

		var profile = new Profile();
		profile.setIdentity(identity);
		profile.setSystem(System.Consumer);
		profile.setRole(Profile.Role.Consumer);
		profile.setStatus(Status.Pending);
		profile.setPassword(passwordEncoder.encode(form.password()));
		
		profile = profileRepo.save(profile);
		registration.setInitializedAt(LocalDateTime.now());
		
		return new InitializationResult(profile.getId());
	}

	private void checkCooldown(OtpRecord otpRecord) {
		if(otpRecord.getFailCount() >= otpCooldownMissingCount) {
			var waitTo = otpRecord.getCheckedAt().plusMinutes(otpCooldownWait);
			if(waitTo.isAfter(LocalDateTime.now())) {
				throw new BusinessRuleViolationException("You have reached the failed verification attempt limit. Please try again after %s.".formatted(waitTo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
			}
			otpRecord.setFailCount(0);
		}
	}

}
