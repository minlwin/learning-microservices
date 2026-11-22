package com.jdc.consumer.api.management.input;

import java.time.LocalDate;

import com.jdc.consumer.model.entity.AccountLockStatus.Status;

public record LockHistorySearch(
		Status status,
		LocalDate dateFrom,
		LocalDate dateTo,
		String keyword // Name, Phone or email with ignore case start search 
) {

}
