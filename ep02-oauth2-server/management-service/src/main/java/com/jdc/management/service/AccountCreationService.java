package com.jdc.management.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.api.input.AccountRegistrationForm;
import com.jdc.management.event.service.ManagementAccountInvitationOutboxService;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.repo.AccountRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountCreationService {

    private final AccountRepo accounts;
    private final ManagementAccountInvitationOutboxService invitationOutbox;

    @Transactional
    public void create(AccountRegistrationForm form) {
        if (accounts.existsByEmail(form.email())) {
            throw new BusinessRuleViolationException("A management account already exists for this email address.");
        }
        var account = new Account();
        account.setName(form.name());
        account.setPhone(form.phone());
        account.setEmail(form.email());
        account.setStatus(Account.Status.Pending);
        account.setRegisteredAt(Instant.now());
        account = accounts.save(account);
        invitationOutbox.enqueue(account);
    }
}
