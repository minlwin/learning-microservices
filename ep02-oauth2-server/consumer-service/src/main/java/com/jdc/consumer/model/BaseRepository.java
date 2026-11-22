package com.jdc.consumer.model;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

@Validated
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

	/** Executes a Criteria query, including entity or DTO/scalar projections. */
	<R> List<R> search(@NotNull(message = "Search query function must not be null.") Function<CriteriaBuilder, CriteriaQuery<R>> searchFunc);

	/** Uses a zero-based page; both queries must apply equivalent filters. The count query must return one total. */
	<R> Page<R> search(
			@NotNull(message = "Search query function must not be null.") Function<CriteriaBuilder, CriteriaQuery<R>> searchFunc,
			@NotNull(message = "Count query function must not be null.") Function<CriteriaBuilder, CriteriaQuery<Long>> countFunc,
			@Min(value = 0, message = "Page index must not be negative.") int page,
			@Min(value = 1, message = "Page size must be positive.") int size);
}
