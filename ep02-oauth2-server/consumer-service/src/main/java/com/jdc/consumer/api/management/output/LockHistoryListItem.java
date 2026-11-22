package com.jdc.consumer.api.management.output;

import java.time.Instant;
import java.util.UUID;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.entity.PinLockHistory.LockType;

/** Lock events with the account's current lock state (not its historical state). */
public record LockHistoryListItem(
		UUID id,
		UUID accountId,
		LockType lockType,
		Instant lockAt,
		AccountLockStatus.Status lockStatus,
		Instant statusChangeAt,
		String name,
		String email,
		String phone) {
}
