package com.jdc.consumer.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.api.management.input.LockHistorySearch;
import com.jdc.consumer.api.management.output.LockHistoryListItem;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.exceptions.ResourceNotFoundException;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.query.LockHistoryQuery;
import com.jdc.consumer.model.repo.AccountLockStatusRepo;
import com.jdc.consumer.model.repo.PinLockHistoryRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LockStatusService {
    private final PinLockHistoryRepo histories;
    private final AccountLockStatusRepo locks;
    private final LockHistoryQuery historyQuery;

    @Transactional
    public void unlock(UUID id) {
        var state = locks.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("No lock status was found for the provided account ID."));
        if (state.getStatus() != AccountLockStatus.Status.Locked) {
            throw new BusinessRuleViolationException("The consumer account is not locked.");
        }
        state.setStatus(AccountLockStatus.Status.Active);
        state.setStatusChangeAt(Instant.now());
        // TODO: Reliably notify auth-server after commit when the account-status integration is implemented.
        // Preserve previous lock events; unlocking does not change account approval or access status.
    }

    /** Date filters are inclusive UTC dates of the history event. */
    public Page<LockHistoryListItem> search(LockHistorySearch form, int page, int size) {
        if (form.dateFrom() != null && form.dateTo() != null && form.dateFrom().isAfter(form.dateTo())) {
            throw new BusinessRuleViolationException("The start date must not be after the end date.");
        }
        return histories.search(
                builder -> historyQuery.select(builder, form),
                builder -> historyQuery.count(builder, form),
                page, size);
    }
}
