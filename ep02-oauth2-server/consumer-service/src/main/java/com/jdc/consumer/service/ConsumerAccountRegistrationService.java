package com.jdc.consumer.service;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.api.management.input.ConsumerRegistrationForm;
import com.jdc.consumer.api.mobile.input.SelfRegistrationForm;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountAccess;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.repo.AccountAccessRepo;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
import com.jdc.consumer.model.repo.AccountRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsumerAccountRegistrationService {

    private final AccountRepo accounts;
    private final AccountAccessRepo accesses;
    private final AccountLockStatusRepo locks;

    @Transactional
    public void create(ConsumerRegistrationForm form) {
        create(form.name(), form.email(), form.phone(), form.dob(), form.address(),
                Account.RegistrationType.Management);
    }

    @Transactional
    public void create(SelfRegistrationForm form) {
        create(form.name(), form.email(), form.phone(), form.dob(), form.address(),
                Account.RegistrationType.Self);
    }

    private void create(String name, String email, String phone, LocalDate dateOfBirth, String address,
            Account.RegistrationType registrationType) {
        if (accounts.existsByEmail(email)) {
            throw new BusinessRuleViolationException("A consumer account already exists for this email address.");
        }

        var now = Instant.now();
        var account = new Account();
        account.setName(name);
        account.setEmail(email);
        account.setPhone(phone);
        account.setDateOfBirth(dateOfBirth);
        account.setAddress(address);
        account.setRegistrationType(registrationType);
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
