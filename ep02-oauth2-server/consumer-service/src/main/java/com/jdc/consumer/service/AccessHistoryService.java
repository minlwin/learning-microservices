package com.jdc.consumer.service;

import com.jdc.consumer.model.entity.dto.AccountAccessPk_;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jdc.consumer.api.management.input.AccessHistorySearch;
import com.jdc.consumer.api.management.output.AccessHistoryListItem;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.Account_;
import com.jdc.consumer.model.entity.AccessHistory;
import com.jdc.consumer.model.entity.AccessHistory_;
import com.jdc.consumer.model.repo.AccessHistoryRepo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessHistoryService {
	private final AccessHistoryRepo histories;

	/** Date filters are inclusive UTC dates of the history event. */
	public Page<AccessHistoryListItem> search(AccessHistorySearch form, int page, int size) {
		if (form.dateFrom() != null && form.dateTo() != null && form.dateFrom().isAfter(form.dateTo())) {
			throw new BusinessRuleViolationException("The start date must not be after the end date.");
		}
		return histories.search(builder -> {
			var query = builder.createQuery(AccessHistoryListItem.class);
			var history = query.from(AccessHistory.class);
			var account = history.join(AccessHistory_.account);
			query.select(builder.construct(AccessHistoryListItem.class,
					account.get(Account_.id), history.get(AccessHistory_.id).get(AccountAccessPk_.revision),
					history.get(AccessHistory_.status), history.get(AccessHistory_.statusChangeAt),
					account.get(Account_.name), account.get(Account_.email), account.get(Account_.status),
					account.get(Account_.registeredAt), account.get(Account_.approvedAt)));
			query.where(filters(builder, history, account, form));
			query.orderBy(builder.desc(history.get(AccessHistory_.statusChangeAt)), builder.asc(account.get(Account_.id)), builder.desc(history.get(AccessHistory_.id).get(AccountAccessPk_.revision)));
			return query;
		}, builder -> {
			var query = builder.createQuery(Long.class);
			var history = query.from(AccessHistory.class);
			var account = history.join(AccessHistory_.account);
			query.select(builder.count(history));
			query.where(filters(builder, history, account, form));
			return query;
		}, page, size);
	}

	private Predicate[] filters(CriteriaBuilder builder, Root<AccessHistory> history, From<?, Account> account, AccessHistorySearch form) {
		var predicates = new ArrayList<Predicate>();
		if (form.status() != null) {
			predicates.add(builder.equal(history.get(AccessHistory_.status), form.status()));
		}
		if (form.dateFrom() != null) {
			predicates.add(builder.greaterThanOrEqualTo(history.get(AccessHistory_.statusChangeAt), form.dateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
		}
		if (form.dateTo() != null) {
			predicates.add(builder.lessThan(history.get(AccessHistory_.statusChangeAt), form.dateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
		}
		if (form.keyword() != null && !form.keyword().isBlank()) {
			var keyword = form.keyword().strip().toLowerCase(Locale.ROOT);
			var pattern = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			predicates.add(builder.or(
					builder.like(builder.lower(account.get(Account_.name)), pattern, '\\'),
					builder.like(builder.lower(account.get(Account_.email)), pattern, '\\'),
					builder.like(builder.lower(account.get(Account_.phone)), pattern, '\\')));
		}
		return predicates.toArray(Predicate[]::new);
	}
}
