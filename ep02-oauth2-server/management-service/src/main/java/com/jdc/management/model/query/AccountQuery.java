package com.jdc.management.model.query;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.jdc.management.api.input.AccountSearch;
import com.jdc.management.api.output.AccountListItem;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.entity.Account_;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Builds fresh Criteria trees for each account search and count query. */
@Component
public class AccountQuery {

    public CriteriaQuery<AccountListItem> select(CriteriaBuilder builder, AccountSearch form) {
        var query = builder.createQuery(AccountListItem.class);
        var account = query.from(Account.class);
        query.select(builder.construct(AccountListItem.class,
                account.get(Account_.id), account.get(Account_.name),
                account.get(Account_.phone), account.get(Account_.email),
                account.get(Account_.status), account.get(Account_.registeredAt),
                account.get(Account_.activatedAt)));
        query.where(filters(builder, account, form));
        query.orderBy(builder.desc(account.get(Account_.registeredAt)), builder.asc(account.get(Account_.id)));
        return query;
    }

    public CriteriaQuery<Long> count(CriteriaBuilder builder, AccountSearch form) {
        var query = builder.createQuery(Long.class);
        var account = query.from(Account.class);
        query.select(builder.count(account));
        query.where(filters(builder, account, form));
        return query;
    }

    private Predicate[] filters(CriteriaBuilder builder, Root<Account> account, AccountSearch form) {
        var predicates = new ArrayList<Predicate>();
        
        if (form.status() != null) {
            predicates.add(builder.equal(account.get(Account_.status), form.status()));
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
