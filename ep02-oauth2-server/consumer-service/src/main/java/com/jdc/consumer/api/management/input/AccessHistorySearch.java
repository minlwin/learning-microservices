package com.jdc.consumer.api.management.input;

import java.time.LocalDate;

import com.jdc.consumer.model.entity.AccountAccess.Status;

public record AccessHistorySearch(
		Status status,
		LocalDate dateFrom,
		LocalDate dateTo,
		String keyword // case ignore start search for name or email or phone
) {

}
