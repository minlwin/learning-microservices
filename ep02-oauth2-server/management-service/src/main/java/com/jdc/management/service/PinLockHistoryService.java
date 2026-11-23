package com.jdc.management.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.management.api.input.PinLockHistorySearch;
import com.jdc.management.api.output.PinLockHistoryListItem;
import com.jdc.management.exceptions.BusinessRuleViolationException;
import com.jdc.management.model.query.PinLockHistoryQuery;
import com.jdc.management.model.repo.PinLockHistoryRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinLockHistoryService {

    private final PinLockHistoryRepo histories;
    private final PinLockHistoryQuery historyQuery;

    /** Date filters are inclusive UTC calendar dates. */
    public Page<PinLockHistoryListItem> search(PinLockHistorySearch form, int page, int size) {
        PageRequest.of(page, size);

        if (form.dateFrom() != null && form.dateTo() != null && form.dateFrom().isAfter(form.dateTo())) {
            throw new BusinessRuleViolationException("The start date must not be after the end date.");
        }

        return histories.search(
                builder -> historyQuery.select(builder, form),
                builder -> historyQuery.count(builder, form),
                page, size);
    }
}
