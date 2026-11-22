package com.jdc.auth.service;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.jdc.auth.AuthTestProperties;
import com.jdc.auth.props.OtpCodeProps;

import com.jdc.auth.api.input.InitializationForm;
import com.jdc.auth.api.input.OtpForm;
import com.jdc.auth.exceptions.BusinessRuleViolationException;
import com.jdc.auth.exceptions.OtpVerificationException;
import com.jdc.auth.model.entity.Activation;
import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.Identity;
import com.jdc.auth.model.entity.dto.OtpRecord;
import com.jdc.auth.model.entity.dto.OtpRecord.CheckStatus;
import com.jdc.auth.model.repo.*;

class ActivationServiceTest {
	private final ActivationRepo activations = mock(ActivationRepo.class);
	private final IdentityRepo identities = mock(IdentityRepo.class);
	private final ProfileRepo profiles = mock(ProfileRepo.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);
	private final OtpCodeService mail = mock(OtpCodeService.class);
	private ActivationService service;
	private Activation activation;
	private OtpRecord otp;

	@BeforeEach
	void setup() {
		service = new ActivationService(AuthTestProperties.create(), new OtpCodeProps(), activations, identities, profiles, encoder, mail);



		activation = new Activation();
		activation.setId(UUID.randomUUID());
		otp = new OtpRecord();
		otp.setStatus(CheckStatus.Send);
		otp.setOtpCode("encoded");
		otp.setSendAt(Instant.now());
		otp.setExpireAt(Instant.now().plus(Duration.ofMinutes(3)));
		activation.setOtpRecord(otp);
		when(activations.findById(activation.getId())).thenReturn(Optional.of(activation));
	}

	@Test
	void acceptsValidUnexpiredCode() {
		when(encoder.matches("123456", "encoded")).thenReturn(true);
		service.validateOtp(activation.getId(), new OtpForm("123456"));
		assertEquals(CheckStatus.Success, otp.getStatus());
	}

	@Test
	void rejectsExpiredCode() {
		otp.setExpireAt(Instant.now().minusSeconds(1));
		assertThrows(BusinessRuleViolationException.class,
				() -> service.validateOtp(activation.getId(), new OtpForm("123456")));
		verifyNoInteractions(encoder);
	}

	@Test
	void recordsFailureAndAllowsRetryBeforeLimit() {
		assertThrows(OtpVerificationException.class,
				() -> service.validateOtp(activation.getId(), new OtpForm("000000")));
		assertEquals(1, otp.getFailCount());
		assertNotNull(otp.getCheckedAt());
		when(encoder.matches("123456", "encoded")).thenReturn(true);
		service.validateOtp(activation.getId(), new OtpForm("123456"));
		assertEquals(CheckStatus.Success, otp.getStatus());
	}

	@Test
	void blocksVerificationDuringCooldown() {
		otp.setFailCount(3);
		otp.setCheckedAt(Instant.now());
		assertThrows(BusinessRuleViolationException.class,
				() -> service.validateOtp(activation.getId(), new OtpForm("123456")));
		verifyNoInteractions(encoder);
	}

	@Test
	void preventsImmediateResend() {
		assertThrows(BusinessRuleViolationException.class,
				() -> service.resendOtp(activation.getId()));
		verifyNoInteractions(mail);
	}

	@Test
	void preventsRepeatedInitialization() {
		activation.setInitializedAt(Instant.now());
		assertThrows(BusinessRuleViolationException.class,
				() -> service.initialize(activation.getId(), new InitializationForm("password")));
		verifyNoInteractions(profiles);
	}
	@Test
	void sendsFirstCodeAndStoresItsHash() {
		activation.setEmail("manager@example.com");
		activation.setOtpRecord(null);

		when(mail.sendForActivation("manager@example.com", 5)).thenReturn("123456");
		when(encoder.encode("123456")).thenReturn("hashed");
		service.request(activation.getId());
		var sent = activation.getOtpRecord();
		assertEquals("hashed", sent.getOtpCode());
		assertEquals(CheckStatus.Send, sent.getStatus());
		assertEquals(1, sent.getSendCount());
		assertEquals(sent.getSendAt().plus(Duration.ofMinutes(5)), sent.getExpireAt());
	}

	@Test
	void initializesActiveManagementProfileAndConsumesInvitation() {
		activation.setEmail("manager@example.com");
		otp.setStatus(CheckStatus.Success);
		var identity = new Identity();
		when(identities.findOneByUsername("manager@example.com")).thenReturn(Optional.of(identity));
		when(encoder.encode("password")).thenReturn("hashed-password");
		when(profiles.save(any(Profile.class))).thenAnswer(invocation -> {
			Profile profile = invocation.getArgument(0);
			profile.setId(UUID.randomUUID());
			return profile;
		});
		var result = service.initialize(activation.getId(), new InitializationForm("password"));
		var captor = ArgumentCaptor.forClass(Profile.class);
		verify(profiles).save(captor.capture());
		assertEquals("Management", captor.getValue().getSystem());
		assertEquals("Management", captor.getValue().getRole());
		assertEquals(Profile.Status.Active, captor.getValue().getStatus());
		assertEquals("hashed-password", captor.getValue().getPassword());
		assertEquals(captor.getValue().getId(), result.id());
		assertNotNull(activation.getInitializedAt());
	}

}
