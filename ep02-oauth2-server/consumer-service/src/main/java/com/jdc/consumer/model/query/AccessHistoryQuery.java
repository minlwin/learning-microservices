package com.jdc.consumer.model.query;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.jdc.consumer.api.management.input.AccessHistorySearch;
import com.jdc.consumer.api.management.output.AccessHistoryListItem;
import com.jdc.consumer.model.entity.AccessHistory;
import com.jdc.consumer.model.entity.AccessHistory_;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.Account_;
import com.jdc.consumer.model.entity.dto.AccountAccessPk_;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Builds fresh query roots and predicates for each search and count. */
@Component
public class AccessHistoryQuery {

    public CriteriaQuery<AccessHistoryListItem> select(CriteriaBuilder builder, AccessHistorySearch form) {
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
    }

    public CriteriaQuery<Long> count(CriteriaBuilder builder, AccessHistorySearch form) {
        var query = builder.createQuery(Long.class);
        var history = query.from(AccessHistory.class);
        var account = history.join(AccessHistory_.account);
        query.select(builder.count(history));
        query.where(filters(builder, history, account, form));
        return query;
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
