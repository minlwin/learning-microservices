package com.jdc.consumer.service;

import com.jdc.consumer.model.query.ConsumerAccountQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jdc.consumer.api.management.input.ConsumerRegistrationForm;
import com.jdc.consumer.api.mobile.input.SelfRegistrationForm;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountAccess;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.entity.OutboxEvent;
import com.jdc.consumer.model.repo.AccountRepo;
import com.jdc.consumer.model.repo.AccountAccessRepo;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
import com.jdc.consumer.model.repo.OutboxEventRepo;
import com.jdc.consumer.props.KafkaProps;
import com.jdc.consumer.event.ConsumerAccountApproved;
import tools.jackson.databind.ObjectMapper;

class ConsumerAccountServiceTest {
	private final AccountRepo accounts = mock(AccountRepo.class);
	private final AccountAccessRepo accesses = mock(AccountAccessRepo.class);
	private final AccountLockStatusRepo locks = mock(AccountLockStatusRepo.class);
	private final OutboxEventRepo outboxEvents = mock(OutboxEventRepo.class);
	private final AuthConsumerProfileCheckService authProfileCheck = mock(AuthConsumerProfileCheckService.class);
	private final ObjectMapper objectMapper = mock(ObjectMapper.class);
	private final KafkaProps kafkaProperties = new KafkaProps();
	private final ConsumerAccountService service = new ConsumerAccountService(accounts, accesses, locks, outboxEvents,
			new ConsumerAccountQuery(), authProfileCheck, kafkaProperties, objectMapper);
	private final SelfRegistrationForm form = new SelfRegistrationForm("Alice", "alice@example.com", "1234", LocalDate.of(1990, 1, 1), "Address");

	@BeforeEach
	void setup() {
		when(accounts.save(any())).thenAnswer(call -> {
			Account account = call.getArgument(0);
			account.setId(UUID.randomUUID());
			return account;
		});
	}

	@Test
	void initializesAppliedAccountWithLogoutAndActiveLockState() {
		service.register(form);
		var captured = ArgumentCaptor.forClass(Account.class);
		verify(accounts).save(captured.capture());
		var account = captured.getValue();
		assertEquals(Account.RegistrationType.Self, account.getRegistrationType());
		assertEquals(Account.Status.Applied, account.getStatus());
		assertEquals(form.dob(), account.getDateOfBirth());
		assertNull(account.getApprovedAt());
		assertEquals(AccountAccess.Status.Logout, account.getAccess().getStatus());
		assertEquals(AccountLockStatus.Status.Active, account.getLockStatus().getStatus());
		assertSame(account, account.getAccess().getAccount());
		assertSame(account, account.getLockStatus().getAccount());
		assertEquals(account.getRegisteredAt(), account.getLockStatus().getStatusChangeAt());
		verify(accesses).save(account.getAccess());
		verify(locks).save(account.getLockStatus());
	}

	@Test
	void recordsManagementOriginWithoutApprovingAutomatically() {
		service.register(new ConsumerRegistrationForm(form.name(), form.email(), form.phone(), form.dob(), form.address()));
		var captured = ArgumentCaptor.forClass(Account.class);
		verify(accounts).save(captured.capture());
		assertEquals(Account.RegistrationType.Management, captured.getValue().getRegistrationType());
		assertEquals(Account.Status.Applied, captured.getValue().getStatus());
		verify(authProfileCheck).requireNoConsumerProfile(form.email());
		verifyNoInteractions(outboxEvents);
	}

	@Test
	void rejectsDuplicateEmailBeforeWritingState() {
		when(accounts.existsByEmail(form.email())).thenReturn(true);
		assertThrows(BusinessRuleViolationException.class, () -> service.register(form));
		verify(accounts, never()).save(any());
		verifyNoInteractions(accesses, locks);
	}

	@Test
	void approvesOnlyAppliedAccounts() throws Exception {
		var account = new Account();
		account.setId(UUID.randomUUID());
		account.setEmail(form.email());
		account.setRegistrationType(Account.RegistrationType.Self);
		account.setStatus(Account.Status.Applied);
		when(accounts.findForUpdate(account.getId())).thenReturn(Optional.of(account));
		when(objectMapper.writeValueAsString(any(ConsumerAccountApproved.class))).thenReturn("{}");
		service.approve(account.getId());
		assertEquals(Account.Status.Approved, account.getStatus());
		assertNotNull(account.getApprovedAt());
		var eventCaptor = ArgumentCaptor.forClass(ConsumerAccountApproved.class);
		verify(objectMapper).writeValueAsString(eventCaptor.capture());
		assertEquals(account.getId(), eventCaptor.getValue().consumerAccountId());
		assertEquals("Self", eventCaptor.getValue().registrationType());
		var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEvents).save(outboxCaptor.capture());
		assertEquals(kafkaProperties.getAccount().getApprovedTopic(), outboxCaptor.getValue().getTopic());
		assertThrows(BusinessRuleViolationException.class, () -> service.approve(account.getId()));
		assertThrows(ResourceNotFoundException.class, () -> service.approve(UUID.randomUUID()));
	}

	@Test
	void rejectsOnlyAppliedAccountsAndRecordsAnOutboxEvent() throws Exception {
		var account = new Account();
		account.setId(UUID.randomUUID());
		account.setEmail(form.email());
		account.setRegistrationType(Account.RegistrationType.Self);
		account.setStatus(Account.Status.Applied);
		when(accounts.findForUpdate(account.getId())).thenReturn(Optional.of(account));
		when(objectMapper.writeValueAsString(any(com.jdc.consumer.event.ConsumerAccountRejected.class))).thenReturn("{}");

		service.reject(account.getId());

		assertEquals(Account.Status.Rejected, account.getStatus());
		assertNotNull(account.getRejectedAt());
		verify(outboxEvents).save(argThat(event -> kafkaProperties.getAccount().getRejectedTopic().equals(event.getTopic())));
		assertThrows(BusinessRuleViolationException.class, () -> service.reject(account.getId()));
	}

	@Test
	void findsOwnProfileByEmailAndIncludesCurrentStates() {
		var account = new Account();
		account.setEmail(form.email());
		var lock = new AccountLockStatus();
		lock.setStatus(AccountLockStatus.Status.Locked);
		account.setLockStatus(lock);
		when(accounts.findByEmail(form.email())).thenReturn(Optional.of(account));
		var profile = service.findByUsername(form.email());
		assertEquals(form.email(), profile.email());
		assertEquals(AccountLockStatus.Status.Locked, profile.lockStatus());
		assertNull(profile.accessStatus());
		assertThrows(ResourceNotFoundException.class, () -> service.findByUsername("missing@example.com"));
	}
}
