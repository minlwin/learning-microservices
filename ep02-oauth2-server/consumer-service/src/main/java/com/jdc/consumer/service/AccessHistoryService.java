package com.jdc.consumer.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.consumer.api.management.input.AccessHistorySearch;
import com.jdc.consumer.api.management.output.AccessHistoryListItem;
import com.jdc.consumer.exceptions.BusinessRuleViolationException;
import com.jdc.consumer.model.query.AccessHistoryQuery;
import com.jdc.consumer.model.repo.AccessHistoryRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessHistoryService {
    private final AccessHistoryRepo histories;
    private final AccessHistoryQuery historyQuery;

    /** Date filters are inclusive UTC dates of the history event. */
    public Page<AccessHistoryListItem> search(AccessHistorySearch form, int page, int size) {
        if (form.dateFrom() != null && form.dateTo() != null && form.dateFrom().isAfter(form.dateTo())) {
            throw new BusinessRuleViolationException("The start date must not be after the end date.");
        }
        return histories.search(
                builder -> historyQuery.select(builder, form),
                builder -> historyQuery.count(builder, form),
                page, size);
    }
}
