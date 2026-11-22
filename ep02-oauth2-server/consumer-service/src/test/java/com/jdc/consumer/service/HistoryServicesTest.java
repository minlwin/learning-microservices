package com.jdc.consumer.service;

import com.jdc.consumer.model.query.LockHistoryQuery;

import com.jdc.consumer.model.query.AccessHistoryQuery;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import com.jdc.consumer.api.management.input.AccessHistorySearch;
import com.jdc.consumer.api.management.input.LockHistorySearch;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.BaseRepositoryImpl;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountAccess;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.entity.AccessHistory;
import com.jdc.consumer.model.entity.PinLockHistory;
import com.jdc.consumer.model.entity.dto.AccountAccessPk;
import com.jdc.consumer.model.repo.AccessHistoryRepo;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
import com.jdc.consumer.model.repo.PinLockHistoryRepo;

import jakarta.persistence.EntityManager;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryServicesTest {
	private LocalContainerEntityManagerFactoryBean factory;
	private EntityManager em;
	private AccessHistoryService access;
	private LockStatusService locks;
	private Account alice;
	private final LocalDate date = LocalDate.of(2026, 11, 22);

	@BeforeAll
	void setup() {
		factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:histories;DB_CLOSE_DELAY=-1", "sa", ""));
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factory.setPackagesToScan("com.jdc.consumer.model.entity");
		factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
		factory.afterPropertiesSet();
		em = factory.getObject().createEntityManager();
		var repos = new JpaRepositoryFactory(em);
		repos.setRepositoryBaseClass(BaseRepositoryImpl.class);
		access = new AccessHistoryService(repos.getRepository(AccessHistoryRepo.class), new AccessHistoryQuery());
		locks = new LockStatusService(repos.getRepository(PinLockHistoryRepo.class), repos.getRepository(AccountLockStatusRepo.class), new LockHistoryQuery());
		em.getTransaction().begin();
		alice = account("Alice", AccountLockStatus.Status.Locked);
		var bob = account("Bob", AccountLockStatus.Status.Active);
		history(alice, 1, AccountAccess.Status.Login, "2026-11-22T00:00:00Z");
		history(alice, 2, AccountAccess.Status.Logout, "2026-11-22T23:59:59Z");
		history(bob, 1, AccountAccess.Status.Login, "2026-11-23T00:00:00Z");
		lockHistory(alice, "2026-11-22T00:00:00Z");
		lockHistory(alice, "2026-11-22T23:59:59Z");
		lockHistory(bob, "2026-11-23T00:00:00Z");
		em.getTransaction().commit();
		em.clear();
	}

	@AfterAll
	void cleanup() {
		if (em != null) {
			em.close();
		}
		if (factory != null) {
			factory.destroy();
		}
	}

	@Test
	void searchesAccessHistoryWithCorrectTotalOrderingAndDateBoundaries() {
		var page = access.search(new AccessHistorySearch(null, date, date, "AL"), 0, 1);
		assertEquals(2, page.getTotalElements());
		assertEquals(2, page.getContent().getFirst().revision());
		assertEquals(AccountAccess.Status.Logout, page.getContent().getFirst().accessType());
		assertEquals(1, access.search(new AccessHistorySearch(AccountAccess.Status.Login, date, date, "alice@"), 0, 10).getTotalElements());
		var emptyPage = access.search(new AccessHistorySearch(null, date, date, null), 5, 1);
		assertTrue(emptyPage.isEmpty());
		assertEquals(2, emptyPage.getTotalElements());
		assertTrue(access.search(new AccessHistorySearch(null, null, null, "%"), 0, 10).isEmpty());
		assertThrows(BusinessRuleViolationException.class, () -> access.search(new AccessHistorySearch(null, date.plusDays(1), date, null), 0, 10));
	}

	@Test
	void searchesLockEventsUsingCurrentStatusAndMatchingCount() {
		var page = locks.search(new LockHistorySearch(AccountLockStatus.Status.Locked, date, date, "aL"), 0, 1);
		assertEquals(2, page.getTotalElements());
		assertEquals(Instant.parse("2026-11-22T23:59:59Z"), page.getContent().getFirst().lockAt());
		assertEquals(AccountLockStatus.Status.Locked, page.getContent().getFirst().lockStatus());
		assertTrue(locks.search(new LockHistorySearch(AccountLockStatus.Status.Active, date, date, null), 0, 10).isEmpty());
		assertEquals(1, locks.search(new LockHistorySearch(AccountLockStatus.Status.Active, null, null, null), 0, 10).getTotalElements());
		assertThrows(BusinessRuleViolationException.class, () -> locks.search(new LockHistorySearch(null, date.plusDays(1), date, null), 0, 10));
	}

	@Test
	void unlocksCurrentStateWithoutDeletingHistoryOrApprovingAccount() {
		em.getTransaction().begin();
		try {
			locks.unlock(alice.getId());
			em.flush();
			em.clear();
			var state = em.find(AccountLockStatus.class, alice.getId());
			assertEquals(AccountLockStatus.Status.Active, state.getStatus());
			assertTrue(state.getStatusChangeAt().isAfter(Instant.parse("2026-11-22T00:00:00Z")));
			assertEquals(Account.Status.Applied, em.find(Account.class, alice.getId()).getStatus());
			assertEquals(2, locks.search(new LockHistorySearch(null, date, date, "Alice"), 0, 10).getTotalElements());
			assertThrows(BusinessRuleViolationException.class, () -> locks.unlock(alice.getId()));
			assertThrows(ResourceNotFoundException.class, () -> locks.unlock(UUID.randomUUID()));
		} finally {
			em.getTransaction().rollback();
			em.clear();
		}
	}

	private Account account(String name, AccountLockStatus.Status status) {
		var account = new Account();
		account.setEmail(name.toLowerCase() + "@example.com");
		account.setName(name);
		account.setPhone("091234");
		account.setAddress("Address");
		account.setDateOfBirth(LocalDate.of(1990, 1, 1));
		account.setStatus(Account.Status.Applied);
		account.setRegistrationType(Account.RegistrationType.Self);
		account.setRegisteredAt(Instant.parse("2026-11-21T00:00:00Z"));
		em.persist(account);
		var state = new AccountLockStatus();
		state.setAccount(account);
		state.setStatus(status);
		state.setStatusChangeAt(Instant.parse("2026-11-22T00:00:00Z"));
		em.persist(state);
		account.setLockStatus(state);
		return account;
	}

	private void history(Account account, int revision, AccountAccess.Status status, String at) {
		var history = new AccessHistory();
		history.setId(new AccountAccessPk(account.getId(), revision));
		history.setAccount(account);
		history.setStatus(status);
		history.setStatusChangeAt(Instant.parse(at));
		em.persist(history);
	}

	private void lockHistory(Account account, String at) {
		var history = new PinLockHistory();
		history.setAccount(account);
		history.setLockType(PinLockHistory.LockType.Temporary);
		history.setLockAt(Instant.parse(at));
		em.persist(history);
	}
}
