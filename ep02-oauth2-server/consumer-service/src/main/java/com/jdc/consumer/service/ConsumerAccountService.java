package com.jdc.consumer.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.api.management.input.ConsumerAccountSearch;
import com.jdc.consumer.api.management.input.ConsumerRegistrationForm;
import com.jdc.consumer.api.management.output.ConsumerAccountListItem;
import com.jdc.consumer.api.mobile.input.SelfRegistrationForm;
import com.jdc.consumer.api.mobile.output.ConsumerProfile;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountAccess;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.query.ConsumerAccountQuery;
import com.jdc.consumer.model.repo.AccountAccessRepo;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
import com.jdc.consumer.model.repo.AccountRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumerAccountService {
    private final AccountRepo accounts;
    private final AccountAccessRepo accesses;
    private final AccountLockStatusRepo locks;
    private final ConsumerAccountQuery accountQuery;

    /**
     * Date filters represent inclusive UTC calendar dates.
     */
    public Page<ConsumerAccountListItem> search(ConsumerAccountSearch form, int page, int size) {
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
    public void register(ConsumerRegistrationForm form) {
        // TODO: Before management registration, call auth-server
        // POST /api/internal/profiles/email/check with this email and system=Consumer
        // using subsystem credentials authorized through email-check-systems. An
        // identity in
        // another system is allowed. This check is advisory; enforce uniqueness again
        // when the auth server creates the profile to handle concurrent registrations.
        register(form.name(), form.email(), form.phone(), form.dob(), form.address(),
                Account.RegistrationType.Management);
        // TODO: Reliably publish ConsumerRegisteredByManagement after this transaction
        // commits (for example, through an outbox). Include event ID, consumer account
        // ID, email, and system=Consumer; never include credentials. This event creates
        // a CONSUMER invitation, not a Management-system user.
    }

    @Transactional
    public void register(SelfRegistrationForm form) {
        // Self-registration follows auth-server email verification and profile/password
        // initialization. The controller binds the email to the authenticated identity.
        // TODO: Support a restricted onboarding token for Pending auth profiles so they
        // can submit consumer information without gaining approved-account permissions.
        register(form.name(), form.email(), form.phone(), form.dob(), form.address(), Account.RegistrationType.Self);
    }

    public ConsumerProfile findByUsername(String username) {
        var account = accounts.findByEmail(username).orElseThrow(
                () -> new ResourceNotFoundException("No consumer account was found for the authenticated user."));
        return ConsumerProfile.from(account);
    }

    @Transactional
    public void approve(UUID id) {
        var account = accounts.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No consumer account was found for the provided ID."));

        if (account.getStatus() != Account.Status.Applied) {
            throw new BusinessRuleViolationException("Only an applied consumer account can be approved.");
        }

        account.setStatus(Account.Status.Approved);
        account.setApprovedAt(Instant.now());
        // Managed entity changes are flushed by the transaction.
        // Auth activation is a separate workflow.
    }

    private void register(String name, String email, String phone, LocalDate dob, String address,
            Account.RegistrationType type) {
        if (accounts.existsByEmail(email)) {
            throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
        }
        var now = Instant.now();
        var account = new Account();
        account.setName(name);
        account.setEmail(email);
        account.setPhone(phone);
        account.setDateOfBirth(dob);
        account.setAddress(address);
        account.setRegistrationType(type);
        account.setStatus(Account.Status.Applied);
        account.setRegisteredAt(now);
        account = accounts.save(account);

        var access = new AccountAccess();
        access.setAccount(account);
        access.setStatus(AccountAccess.Status.Logout);
        access.setStatusChangeAt(now);
        accesses.save(access);
        account.setAccess(access);

        var lock = new AccountLockStatus();
        lock.setAccount(account);
        lock.setStatus(AccountLockStatus.Status.Active);
        lock.setStatusChangeAt(now);
        locks.save(lock);
        account.setLockStatus(lock);
    }
}
