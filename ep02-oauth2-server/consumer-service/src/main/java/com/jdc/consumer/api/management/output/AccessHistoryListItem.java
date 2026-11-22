package com.jdc.consumer.api.management.output;

import java.time.Instant;
import java.util.UUID;

import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountAccess;

public record AccessHistoryListItem(
		UUID accountId,
		int revision,
		AccountAccess.Status accessType,
		Instant accessAt,
		String name,
		String email,
		Account.Status status,
		Instant registeredAt,
		Instant approvedAt
) {

}
