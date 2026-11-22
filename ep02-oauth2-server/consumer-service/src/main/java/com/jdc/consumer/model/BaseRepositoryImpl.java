package com.jdc.consumer.model;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

@Validated
@Transactional(readOnly = true)
public class BaseRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> implements BaseRepository<T, ID> {

	private final EntityManager entityManager;

	public BaseRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
		super(entityInformation, entityManager);
		this.entityManager = entityManager;
	}

	@Override
	public <R> List<R> search(Function<CriteriaBuilder, CriteriaQuery<R>> searchFunc) {
		var criteria = searchFunc.apply(entityManager.getCriteriaBuilder());
		Assert.notNull(criteria, "Search query function must return a query.");
		return entityManager.createQuery(criteria).getResultList();
	}

	@Override
	public <R> Page<R> search(Function<CriteriaBuilder, CriteriaQuery<R>> searchFunc,
			Function<CriteriaBuilder, CriteriaQuery<Long>> countFunc, int page, int size) {
		var pageable = PageRequest.of(page, size);
		Assert.isTrue(pageable.getOffset() <= Integer.MAX_VALUE, "Page offset exceeds the JPA query limit.");

		var builder = entityManager.getCriteriaBuilder();
		var criteria = searchFunc.apply(builder);
		Assert.notNull(criteria, "Search query function must return a query.");
		var query = entityManager.createQuery(criteria);
		query.setFirstResult((int) pageable.getOffset());
		query.setMaxResults(pageable.getPageSize());
		var content = query.getResultList();

		var countCriteria = countFunc.apply(builder);
		Assert.notNull(countCriteria, "Count query function must return a query.");
		var total = entityManager.createQuery(countCriteria).getSingleResult();
		return new PageImpl<>(content, pageable, total);
	}
}
