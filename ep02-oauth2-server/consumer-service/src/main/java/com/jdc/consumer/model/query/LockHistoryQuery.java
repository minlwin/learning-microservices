package com.jdc.consumer.model.query;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.jdc.consumer.api.management.input.LockHistorySearch;
import com.jdc.consumer.api.management.output.LockHistoryListItem;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountLockStatus_;
import com.jdc.consumer.model.entity.Account_;
import com.jdc.consumer.model.entity.PinLockHistory;
import com.jdc.consumer.model.entity.PinLockHistory_;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Builds fresh query roots and predicates for each search and count. */
@Component
public class LockHistoryQuery {

    public CriteriaQuery<LockHistoryListItem> select(CriteriaBuilder builder, LockHistorySearch form) {
        var query = builder.createQuery(LockHistoryListItem.class);
        var history = query.from(PinLockHistory.class);
        var account = history.join(PinLockHistory_.account);
        var state = account.join(Account_.lockStatus, JoinType.LEFT);
        query.select(builder.construct(LockHistoryListItem.class,
                history.get(PinLockHistory_.id), account.get(Account_.id), history.get(PinLockHistory_.lockType),
                history.get(PinLockHistory_.lockAt), state.get(AccountLockStatus_.status), state.get(AccountLockStatus_.statusChangeAt),
                account.get(Account_.name), account.get(Account_.email), account.get(Account_.phone)));
        query.where(filters(builder, history, account, form));
        query.orderBy(builder.desc(history.get(PinLockHistory_.lockAt)), builder.asc(account.get(Account_.id)), builder.desc(history.get(PinLockHistory_.id)));
        return query;
    }

    public CriteriaQuery<Long> count(CriteriaBuilder builder, LockHistorySearch form) {
        var query = builder.createQuery(Long.class);
        var history = query.from(PinLockHistory.class);
        var account = history.join(PinLockHistory_.account);
        query.select(builder.count(history));
        query.where(filters(builder, history, account, form));
        return query;
    }

    private Predicate[] filters(CriteriaBuilder builder, Root<PinLockHistory> history, From<?, Account> account, LockHistorySearch form) {
        var predicates = new ArrayList<Predicate>();
        if (form.status() != null) {
            predicates.add(builder.equal(account.join(Account_.lockStatus).get(AccountLockStatus_.status), form.status()));
        }
        if (form.dateFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(history.get(PinLockHistory_.lockAt), form.dateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (form.dateTo() != null) {
            predicates.add(builder.lessThan(history.get(PinLockHistory_.lockAt), form.dateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
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
