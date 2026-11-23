package com.jdc.management.model.query;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.jdc.management.api.input.PinLockHistorySearch;
import com.jdc.management.api.output.PinLockHistoryListItem;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.entity.Account_;
import com.jdc.management.model.entity.PinLockHistory;
import com.jdc.management.model.entity.PinLockHistory_;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Builds fresh Criteria trees for PIN lock history and count queries. */
@Component
public class PinLockHistoryQuery {

    public CriteriaQuery<PinLockHistoryListItem> select(CriteriaBuilder builder, PinLockHistorySearch form) {
        var query = builder.createQuery(PinLockHistoryListItem.class);
        var history = query.from(PinLockHistory.class);
        var account = history.join(PinLockHistory_.account);
        query.select(builder.construct(PinLockHistoryListItem.class,
                history.get(PinLockHistory_.id), account.get(Account_.id),
                history.get(PinLockHistory_.lockType), history.get(PinLockHistory_.lockAt),
                history.get(PinLockHistory_.lockedUntil), history.get(PinLockHistory_.requestedBy),
                account.get(Account_.name), account.get(Account_.email), account.get(Account_.phone)));
        query.where(filters(builder, history, account, form));
        query.orderBy(builder.desc(history.get(PinLockHistory_.lockAt)),
                builder.asc(account.get(Account_.id)), builder.desc(history.get(PinLockHistory_.id)));
        return query;
    }

    public CriteriaQuery<Long> count(CriteriaBuilder builder, PinLockHistorySearch form) {
        var query = builder.createQuery(Long.class);
        var history = query.from(PinLockHistory.class);
        var account = history.join(PinLockHistory_.account);
        query.select(builder.count(history));
        query.where(filters(builder, history, account, form));
        return query;
    }

    private Predicate[] filters(CriteriaBuilder builder, Root<PinLockHistory> history,
            From<?, Account> account, PinLockHistorySearch form) {
        var predicates = new ArrayList<Predicate>();

        if (form.lockType() != null) {
            predicates.add(builder.equal(history.get(PinLockHistory_.lockType), form.lockType()));
        }

        if (form.dateFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(history.get(PinLockHistory_.lockAt),
                    form.dateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        if (form.dateTo() != null) {
            predicates.add(builder.lessThan(history.get(PinLockHistory_.lockAt),
                    form.dateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
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
