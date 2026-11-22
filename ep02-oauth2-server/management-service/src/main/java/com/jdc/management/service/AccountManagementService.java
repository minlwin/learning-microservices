package com.jdc.management.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jdc.management.api.input.AccountRegistrationForm;
import com.jdc.management.api.input.AccountSearch;
import com.jdc.management.api.output.AccountListItem;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.exceptions.ResourceNotFoundException;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.repo.AccountRepo;
import com.jdc.management.model.query.AccountQuery;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountManagementService {
    private final AccountRepo accounts;
    private final AuthEmailCheckService emailCheck;
    private final AccountQuery accountQuery;

    /** 
     * Date filters represent inclusive UTC calendar dates. 
     */
    public Page<AccountListItem> search(AccountSearch form, int page, int size) {
        PageRequest.of(page, size);
        
        if (form.createdFrom() != null && form.createdTo() != null && form.createdFrom().isAfter(form.createdTo())) {
            throw new BusinessRuleViolationException("The start date must not be after the end date.");
        }
        
        return accounts.search(
                builder -> accountQuery.select(builder, form),
                builder -> accountQuery.count(builder, form),
                page, size);
    }

    @Transactional
    public void create(AccountRegistrationForm form) {
        if (accounts.existsByEmail(form.email())) {
            throw new BusinessRuleViolationException("A management account already exists for this email address.");
        }
        emailCheck.checkAvailable(form.email());
        var account = new Account();
        account.setName(form.name());
        account.setPhone(form.phone());
        account.setEmail(form.email());
        account.setStatus(Account.Status.Pending);
        account.setRegisteredAt(Instant.now());
        accounts.save(account);
        // TODO: Publish a management-account invitation after commit using an outbox.
        // Auth-server must enforce email uniqueness again when creating the profile.
        // TODO: Consume activation events after password initialization and set
        // status=Activated and activatedAt, with idempotent event processing.
    }

    @Transactional
    public void update(UUID id, AccountRegistrationForm form) {
        var account = accounts.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("No management account was found for the provided ID."));
        if (!account.getEmail().equals(form.email())) {
            throw new BusinessRuleViolationException("Email cannot be changed through this endpoint.");
        }
        account.setName(form.name());
        account.setPhone(form.phone());
        accounts.save(account);
    }

}
