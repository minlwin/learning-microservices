package com.jdc.management.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import jakarta.validation.ConstraintViolationException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import com.jdc.management.model.entity.Account;
import com.jdc.management.model.repo.AccountRepo;
import com.jdc.management.api.input.AccountSearch;
import com.jdc.management.service.AccountManagementService;
import com.jdc.management.model.query.AccountQuery;
import java.time.LocalDate;
import com.jdc.management.model.entity.Account_;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseRepositoryImplTest {
	private LocalContainerEntityManagerFactoryBean factory;
	private EntityManager entityManager;
	private LocalValidatorFactoryBean validator;
	private SearchRepository repository;

	public interface SearchRepository extends AccountRepo {
	}

	@BeforeAll
	void setup() {
		factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:management_repository;DB_CLOSE_DELAY=-1", "sa", ""));
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factory.setPackagesToScan("com.jdc.management.model.entity");
		factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
		factory.afterPropertiesSet();
		entityManager = factory.getObject().createEntityManager();
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		var repositories = new ValidatedRepositoryFactoryBean<SearchRepository, Account, UUID>(SearchRepository.class);
		repositories.setEntityManager(entityManager);
		repositories.setValidator(validator);
		repositories.setRepositoryBaseClass(BaseRepositoryImpl.class);
		repositories.afterPropertiesSet();
		repository = repositories.getObject();

		entityManager.getTransaction().begin();
		persist("Alice");
		persist("Alan");
		persist("Bob");
		entityManager.getTransaction().commit();
		entityManager.clear();
	}

	@AfterAll
	void cleanup() {
		if (validator != null) {
			validator.close();
		}
		if (entityManager != null) {
			entityManager.close();
		}
		if (factory != null) {
			factory.destroy();
		}
	}

    @Test
    void searchesManagementAccountsWithMatchingCountAndEscapedPrefix() {
        var service = new AccountManagementService(repository, null, new AccountQuery());
        var form = new AccountSearch(null, LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 21), "al");
        var page = service.search(form, 0, 1);
        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertTrue(page.getContent().getFirst().name().startsWith("Al"));
        assertEquals(0, service.search(new AccountSearch(null, null, null, "%"), 0, 10).getTotalElements());
        assertEquals(0, service.search(new AccountSearch(null, LocalDate.of(2026, 11, 22), null, null), 0, 10).getTotalElements());
    }

	@Test
	void preservesInstantTimestampsAfterReloading() {
		var expected = Instant.parse("2026-11-21T06:30:00.123456Z");
		assertEquals(3, repository.findAll().size());
		assertTrue(repository.findAll().stream().allMatch(account -> expected.equals(account.getRegisteredAt())));
	}

	@Test
	void filtersAndOrdersScalarProjections() {
		assertEquals(List.of("Alan", "Alice"), repository.search(namesStartingWith("A")));
		assertTrue(repository.search(namesStartingWith("Z")).isEmpty());
	}

	@Test
	void paginatesWithTheFilteredTotal() {
		var first = repository.search(namesStartingWith("A"), countStartingWith("A"), 0, 1);
		assertEquals(List.of("Alan"), first.getContent());
		assertEquals(2, first.getTotalElements());
		assertEquals(2, first.getTotalPages());
		assertTrue(first.hasNext());
		var second = repository.search(namesStartingWith("A"), countStartingWith("A"), 1, 1);
		assertEquals(List.of("Alice"), second.getContent());
		assertEquals(1, second.getNumber());
	}

	@Test
	void retainsTotalForPagesBeyondTheLastResult() {
		var beyond = repository.search(namesStartingWith("A"), countStartingWith("A"), 3, 1);
		assertTrue(beyond.isEmpty());
		assertEquals(2, beyond.getTotalElements());
		var empty = repository.search(namesStartingWith("Z"), countStartingWith("Z"), 0, 10);
		assertEquals(0, empty.getTotalElements());
		assertTrue(empty.isEmpty());
	}

	@Test
	void rejectsInvalidPaginationAndMissingQueryFunctions() {
		assertThrows(ConstraintViolationException.class, () -> repository.search(namesStartingWith("A"), countStartingWith("A"), -1, 10));
		assertThrows(ConstraintViolationException.class, () -> repository.search(namesStartingWith("A"), countStartingWith("A"), 0, 0));
		assertThrows(IllegalArgumentException.class, () -> repository.search(namesStartingWith("A"), countStartingWith("A"), Integer.MAX_VALUE, 2));
		assertThrows(ConstraintViolationException.class, () -> repository.search(null));
		assertThrows(ConstraintViolationException.class, () -> repository.search(namesStartingWith("A"), null, 0, 10));
	}

	@Test
	void rejectsNullQueriesReturnedByFunctions() {
		assertThrows(IllegalArgumentException.class, () -> repository.search(_ -> null));
		assertThrows(IllegalArgumentException.class, () -> repository.search(_ -> null, countStartingWith("A"), 0, 10));
		assertThrows(IllegalArgumentException.class, () -> repository.search(namesStartingWith("A"), _ -> null, 0, 10));
	}

	private Function<CriteriaBuilder, CriteriaQuery<String>> namesStartingWith(String prefix) {
		return builder -> {
			var query = builder.createQuery(String.class);
			var account = query.from(Account.class);
			query.select(account.get(Account_.name));
			query.where(builder.like(account.get(Account_.name), prefix + "%"));
			query.orderBy(builder.asc(account.get(Account_.name)));
			return query;
		};
	}

	private Function<CriteriaBuilder, CriteriaQuery<Long>> countStartingWith(String prefix) {
		return builder -> {
			var query = builder.createQuery(Long.class);
			var account = query.from(Account.class);
			query.select(builder.count(account));
			query.where(builder.like(account.get(Account_.name), prefix + "%"));
			return query;
		};
	}

	private void persist(String name) {
		var account = new Account();
		account.setEmail(name.toLowerCase() + "@example.com");
		account.setName(name);
		account.setPhone("123456789");
		account.setStatus(Account.Status.Pending);
		account.setRegisteredAt(Instant.parse("2026-11-21T06:30:00.123456Z"));
		entityManager.persist(account);
	}
}
