package com.jdc.consumer.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.api.management.input.LockHistorySearch;
import com.jdc.consumer.api.management.output.LockHistoryListItem;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.model.query.LockHistoryQuery;
import com.jdc.consumer.model.repo.PinLockHistoryRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LockStatusService {
    private final PinLockHistoryRepo histories;
    private final LockHistoryQuery historyQuery;
    private final ConsumerPinLockAdminService pinLocks;

    public void unlockPermanentPin(UUID accountId, String requestedBy) {
        pinLocks.unlockPermanentPin(accountId, requestedBy);
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
