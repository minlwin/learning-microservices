package com.jdc.consumer.model.query;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.jdc.consumer.api.management.input.ConsumerAccountSearch;
import com.jdc.consumer.api.management.output.ConsumerAccountListItem;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.Account_;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Builds fresh query roots and predicates for each search and count. */
@Component
public class ConsumerAccountQuery {

    public CriteriaQuery<ConsumerAccountListItem> select(CriteriaBuilder builder, ConsumerAccountSearch form) {
        var query = builder.createQuery(ConsumerAccountListItem.class);
        var account = query.from(Account.class);
        query.select(builder.construct(ConsumerAccountListItem.class, account.get(Account_.id),
                account.get(Account_.name), account.get(Account_.phone), account.get(Account_.email),
                account.get(Account_.status), account.get(Account_.registrationType),
                account.get(Account_.registeredAt), account.get(Account_.approvedAt)));
        query.where(filters(builder, account, form));
        query.orderBy(builder.desc(account.get(Account_.registeredAt)), builder.asc(account.get(Account_.id)));
        return query;
    }

    public CriteriaQuery<Long> count(CriteriaBuilder builder, ConsumerAccountSearch form) {
        var query = builder.createQuery(Long.class);
        var account = query.from(Account.class);
        query.select(builder.count(account));
        query.where(filters(builder, account, form));
        return query;
    }

    private Predicate[] filters(CriteriaBuilder builder, Root<Account> account, ConsumerAccountSearch form) {
        var predicates = new ArrayList<Predicate>();

        if (form.status() != null) {
            predicates.add(builder.equal(account.get(Account_.status), form.status()));
        }

        if (form.registrationType() != null) {
            predicates.add(builder.equal(account.get(Account_.registrationType), form.registrationType()));
        }

        if (form.createdFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(account.get(Account_.registeredAt),
                    form.createdFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        if (form.createdTo() != null) {
            predicates.add(builder.lessThan(account.get(Account_.registeredAt),
                    form.createdTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        if (form.keyword() != null && !form.keyword().isBlank()) {
            var keyword = form.keyword().strip().toLowerCase(Locale.ROOT);
            var pattern = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            predicates.add(builder.or(builder.like(builder.lower(account.get(Account_.name)), pattern, '\\'),
                    builder.like(builder.lower(account.get(Account_.phone)), pattern, '\\'),
                    builder.like(builder.lower(account.get(Account_.email)), pattern, '\\')));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
