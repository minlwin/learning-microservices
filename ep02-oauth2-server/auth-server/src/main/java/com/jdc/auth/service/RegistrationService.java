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
import com.jdc.auth.model.entity.EmailOutboxEvent;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.Profile.Status;
import com.jdc.auth.model.entity.Registration;
import com.jdc.auth.model.entity.dto.OtpRecord;
import com.jdc.auth.model.entity.dto.OtpRecord.CheckStatus;
import com.jdc.auth.model.repo.IdentityRepo;
import com.jdc.auth.model.repo.EmailOutboxEventRepo;
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
	private final LoginTicketService loginTickets;
	private final EmailOutboxEventRepo emailOutboxEvents;
	
	



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

	public void createManagementInvitation(UUID eventId, String email) {
		if (registrationRepo.existsById(eventId)) {
			return;
		}

		var registration = new Registration();
		registration.setId(eventId);
		registration.setEmail(email);
		registration.setRegisteredAt(Instant.now());
		registration.setAccountApproved(true);
		registration.setInvitationExpiresAt(Instant.now().plus(properties.getConsumerInvitationLifetime()));
		var otpRecord = new OtpRecord();
		otpRecord.setSendCount(0);
		otpRecord.setFailCount(0);
		registration.setOtpRecord(otpRecord);
		registrationRepo.save(registration);

		var invitationUrl = properties.getConsumerInvitationUrl().replace("{id}", eventId.toString());
		var emailEvent = new EmailOutboxEvent();
		emailEvent.setId(eventId);
		emailEvent.setRecipient(email);
		emailEvent.setSystem("Consumer");
		emailEvent.setInvitationUrl(invitationUrl);
		emailEvent.setCreatedAt(Instant.now());
		emailEvent.setNextAttemptAt(emailEvent.getCreatedAt());
		emailEvent.setAttemptCount(0);
		emailOutboxEvents.save(emailEvent);
	}

	public void requestOtp(UUID id) {
		var registration = registrationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(NO_DATA_MESSAGE));
		if (!registration.isAccountApproved()) {
			throw new BusinessRuleViolationException("OTP can only be requested from an approved registration.");
		}
		if (registration.getInvitationExpiresAt() == null
				|| !registration.getInvitationExpiresAt().isAfter(Instant.now())) {
			throw new BusinessRuleViolationException("This invitation has expired. Contact support for a new invitation.");
		}
		if (registration.getInitializedAt() != null) {
			throw new BusinessRuleViolationException("This registration has already been used to create an account.");
		}
		var otpRecord = registration.getOtpRecord();
		if (otpRecord != null && otpRecord.getStatus() == CheckStatus.Success) {
			throw new BusinessRuleViolationException("Your email address has already been verified. Continue with account initialization.");
		}
		if (otpRecord != null && otpRecord.getSendAt() != null) {
			resendOtp(id);
			return;
		}
		requestRegistrationOtp(registration);
	}

	@Transactional(dontRollbackOn = OtpVerificationException.class)
	public void validateOtp(UUID id, OtpForm form) {
		
		var registration = registrationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(NO_DATA_MESSAGE));
		if (registration.isAccountApproved() && (registration.getInvitationExpiresAt() == null
				|| !registration.getInvitationExpiresAt().isAfter(Instant.now()))) {
			throw new BusinessRuleViolationException("This invitation has expired. Contact support for a new invitation.");
		}
		
		var otpRecord = registration.getOtpRecord();
		if (otpRecord == null || otpRecord.getStatus() == null || otpRecord.getExpireAt() == null) {
			throw new BusinessRuleViolationException("Please request an email verification code first.");
		}

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
		if (registration.isAccountApproved() && (registration.getInvitationExpiresAt() == null
				|| !registration.getInvitationExpiresAt().isAfter(Instant.now()))) {
			throw new BusinessRuleViolationException("This invitation has expired. Contact support for a new invitation.");
		}
		
		var otpRecord = registration.getOtpRecord();
		if (otpRecord == null) {
			if (!registration.isAccountApproved()) {
				throw new BusinessRuleViolationException("Please request an email verification code first.");
			}
			requestRegistrationOtp(registration);
			return;
		}
		if (otpRecord.getSendAt() == null) {
			requestRegistrationOtp(registration);
			return;
		}

		if(otpRecord.getStatus() == CheckStatus.Success) {
			throw new BusinessRuleViolationException("Your email address has already been verified. You cannot request another verification code.");
		}
		ensureOtpSendAllowed(otpRecord);
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
		PasswordPolicy.validate(form.password());
		// Self registrations receive a restricted, single-use onboarding ticket after
		// initialization. Management invitations create an Active profile here because
		// the Consumer account was already approved before the invitation was issued.
		var registration = registrationRepo.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(NO_DATA_MESSAGE));
		if (registration.isAccountApproved() && (registration.getInvitationExpiresAt() == null
				|| !registration.getInvitationExpiresAt().isAfter(Instant.now()))) {
			throw new BusinessRuleViolationException("This invitation has expired. Contact support for a new invitation.");
		}
		
		if(registration.getInitializedAt() != null) {
			throw new BusinessRuleViolationException("This registration has already been used to create an account.");
		}

		if(registration.getOtpRecord() == null || registration.getOtpRecord().getStatus() != CheckStatus.Success) {
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
		profile.setStatus(registration.isAccountApproved() ? Status.Active : Status.Pending);
		profile.setPassword(passwordEncoder.encode(form.password()));
		
		profile = profileRepo.save(profile);
		registration.setInitializedAt(Instant.now());

		if (registration.isAccountApproved()) {
			return new InitializationResult(profile.getId(), null, 0);
		}
		var ticket = loginTickets.issueOnboarding(profile, properties.getRegistration().getClientId());
		return new InitializationResult(profile.getId(), ticket.loginTicket(), ticket.expiresIn());
	}

	private void requestRegistrationOtp(Registration registration) {
		var otpRecord = registration.getOtpRecord();
		if (otpRecord == null) {
			otpRecord = new OtpRecord();
			registration.setOtpRecord(otpRecord);
		}
		ensureOtpSendAllowed(otpRecord);
		var now = Instant.now();
		var otpCode = otpService.sendForRegistration(registration.getEmail());
		otpRecord.setOtpCode(passwordEncoder.encode(otpCode));
		otpRecord.setStatus(CheckStatus.Send);
		otpRecord.setSendCount(otpRecord.getSendCount() + 1);
		otpRecord.setSendAt(now);
		otpRecord.setExpireAt(now.plus(Duration.ofMinutes(otpProperties.getExpiration().getRegistration())));
	}

	private void checkCooldown(OtpRecord otpRecord) {
		if (otpRecord.getCheckedAt() == null) {
			return;
		}
		if(otpRecord.getFailCount() >= otpProperties.getCooldown().getMissingCount()) {
			var waitTo = otpRecord.getCheckedAt().plus(Duration.ofMinutes(otpProperties.getCooldown().getWaitingDuration()));
			if(waitTo.isAfter(Instant.now())) {
				throw new BusinessRuleViolationException("You have reached the failed verification attempt limit. Please try again after %s.".formatted(waitTo));
			}
			otpRecord.setFailCount(0);
		}
	}

	private void ensureOtpSendAllowed(OtpRecord otpRecord) {
		if (otpRecord.getSendCount() >= otpProperties.getResend().getMaxSends()) {
			throw new BusinessRuleViolationException("The maximum number of verification codes has been sent. Contact support for a new invitation.");
		}
	}

}
