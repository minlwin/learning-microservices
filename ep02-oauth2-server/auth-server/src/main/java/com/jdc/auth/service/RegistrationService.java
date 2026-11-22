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
import com.jdc.auth.api.input.RegistrationForm;
import com.jdc.auth.api.output.InitializationResult;
import com.jdc.auth.api.output.RegistrationResult;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.ResourceNotFoundException;
import com.jdc.auth.exceptions.OtpVerificationException;
import com.jdc.auth.model.entity.Identity;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.Profile.Status;
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
	private final AuthenticationProps properties;
	private final OtpCodeProps otpProperties;

	
	private static final String NO_DATA_MESSAGE = "No registration was found for the provided ID.";

	private final PasswordEncoder passwordEncoder;

	private final EmailValidationService emailValidationService;
	private final OtpCodeService otpService;
	
	private final RegistrationRepo registrationRepo;
	private final IdentityRepo identityRepo;
	private final ProfileRepo profileRepo;
	
	



	public RegistrationResult register(RegistrationForm form) {
		
		emailValidationService.validateForRegistration(form.email());
		
		var registration = new Registration();
		registration.setEmail(form.email());
		registration.setRegisteredAt(Instant.now());
		
		var otpCode = otpService.sendForRegistration(form.email());

		var otpRecord = new OtpRecord();
		otpRecord.setOtpCode(passwordEncoder.encode(otpCode));
		otpRecord.setStatus(CheckStatus.Send);
		otpRecord.setSendCount(1);
		otpRecord.setSendAt(Instant.now());
		otpRecord.setExpireAt(otpRecord.getSendAt().plus(Duration.ofMinutes(otpProperties.getExpiration().getRegistration())));
		
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
		
		if(!otpRecord.getExpireAt().isAfter(Instant.now())) {
			throw new BusinessRuleViolationException("Your verification code has expired. Please request a new code.");
		}
		
		checkCooldown(otpRecord);
		otpRecord.setCheckedAt(Instant.now());
		
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
		var resendAfter = otpRecord.getSendAt().plusSeconds(otpProperties.getResend().getIntervalSeconds());
		if(resendAfter.isAfter(Instant.now())) {
			throw new BusinessRuleViolationException("Please wait until %s before requesting another code.".formatted(resendAfter));
		}

		// Send OTP
		var otpCode = otpService.sendForRegistration(registration.getEmail());
		
		otpRecord.setOtpCode(passwordEncoder.encode(otpCode));
		otpRecord.setStatus(CheckStatus.Send);
		otpRecord.setSendCount(otpRecord.getSendCount() + 1);
		otpRecord.setSendAt(Instant.now());
		otpRecord.setExpireAt(otpRecord.getSendAt().plus(Duration.ofMinutes(otpProperties.getExpiration().getRegistration())));
	}

	public InitializationResult initialize(UUID id, InitializationForm form) {
		// TODO: Return or enable obtaining a restricted onboarding token after profile
		// initialization, allowing Pending consumers to submit KYC to Consumer service.
		// Keep full consumer API permissions unavailable until approval/activation.
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
		
		if(profileRepo.existsByIdentityAndSystem(identity, properties.getRegistration().getSystem())) {
			throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
		}

		var profile = new Profile();
		profile.setIdentity(identity);
		profile.setSystem(properties.getRegistration().getSystem());
		profile.setRole(properties.getRegistration().getRole());
		profile.setStatus(Status.Pending);
		profile.setPassword(passwordEncoder.encode(form.password()));
		
		profile = profileRepo.save(profile);
		registration.setInitializedAt(Instant.now());
		
		return new InitializationResult(profile.getId());
	}

	private void checkCooldown(OtpRecord otpRecord) {
		if(otpRecord.getFailCount() >= otpProperties.getCooldown().getMissingCount()) {
			var waitTo = otpRecord.getCheckedAt().plus(Duration.ofMinutes(otpProperties.getCooldown().getWaitingDuration()));
			if(waitTo.isAfter(Instant.now())) {
				throw new BusinessRuleViolationException("You have reached the failed verification attempt limit. Please try again after %s.".formatted(waitTo));
			}
			otpRecord.setFailCount(0);
		}
	}

}
