package com.jdc.management.api.input;

import java.time.LocalDate;

import com.jdc.management.model.entity.PinLockHistory.LockType;

public record PinLockHistorySearch(
        LockType lockType,
        LocalDate dateFrom,
        LocalDate dateTo,
        String keyword) {

}
