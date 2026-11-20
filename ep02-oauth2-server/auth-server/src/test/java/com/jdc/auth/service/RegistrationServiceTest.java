package com.jdc.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.jdc.auth.api.input.InitializationForm;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.OtpVerificationException;
import com.jdc.auth.model.entity.Registration;
import com.jdc.auth.model.entity.dto.OtpRecord;
import com.jdc.auth.model.entity.dto.OtpRecord.CheckStatus;
import com.jdc.auth.model.repo.*;

class RegistrationServiceTest {
	private final RegistrationRepo registrations = mock(RegistrationRepo.class);
	private final ProfileRepo profiles = mock(ProfileRepo.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);
	private final OtpCodeService mail = mock(OtpCodeService.class);
	private RegistrationService service;
	private Registration registration;
	private OtpRecord otp;

	@BeforeEach
	void setup() {
		service = new RegistrationService(encoder, mock(EmailValidationService.class), mail,
				registrations, mock(IdentityRepo.class), profiles);
		ReflectionTestUtils.setField(service, "otpCooldownMissingCount", 3);
		ReflectionTestUtils.setField(service, "otpCooldownWait", 30);
		ReflectionTestUtils.setField(service, "resendIntervalSeconds", 60);
		registration = new Registration();
		registration.setId(UUID.randomUUID());
		otp = new OtpRecord();
		otp.setStatus(CheckStatus.Send);
		otp.setOtpCode("encoded");
		otp.setSendAt(LocalDateTime.now());
		otp.setExpireAt(LocalDateTime.now().plusMinutes(3));
		registration.setOtpRecord(otp);
		when(registrations.findById(registration.getId())).thenReturn(Optional.of(registration));
	}

	@Test
	void acceptsValidUnexpiredCode() {
		when(encoder.matches("123456", "encoded")).thenReturn(true);
		service.validateOtp(registration.getId(), new OtpForm("123456"));
		assertEquals(CheckStatus.Success, otp.getStatus());
	}

	@Test
	void rejectsExpiredCode() {
		otp.setExpireAt(LocalDateTime.now().minusSeconds(1));
		assertThrows(BusinessRuleViolationException.class,
				() -> service.validateOtp(registration.getId(), new OtpForm("123456")));
		verifyNoInteractions(encoder);
	}

	@Test
	void recordsFailureAndAllowsRetryBeforeLimit() {
		assertThrows(OtpVerificationException.class,
				() -> service.validateOtp(registration.getId(), new OtpForm("000000")));
		assertEquals(1, otp.getFailCount());
		assertNotNull(otp.getCheckedAt());
		when(encoder.matches("123456", "encoded")).thenReturn(true);
		service.validateOtp(registration.getId(), new OtpForm("123456"));
		assertEquals(CheckStatus.Success, otp.getStatus());
	}

	@Test
	void blocksVerificationDuringCooldown() {
		otp.setFailCount(3);
		otp.setCheckedAt(LocalDateTime.now());
		assertThrows(BusinessRuleViolationException.class,
				() -> service.validateOtp(registration.getId(), new OtpForm("123456")));
		verifyNoInteractions(encoder);
	}

	@Test
	void preventsImmediateResend() {
		assertThrows(BusinessRuleViolationException.class,
				() -> service.resendOtp(registration.getId()));
		verifyNoInteractions(mail);
	}

	@Test
	void preventsRepeatedInitialization() {
		registration.setInitializedAt(LocalDateTime.now());
		assertThrows(BusinessRuleViolationException.class,
				() -> service.initialize(registration.getId(), new InitializationForm("password")));
		verifyNoInteractions(profiles);
	}
}
