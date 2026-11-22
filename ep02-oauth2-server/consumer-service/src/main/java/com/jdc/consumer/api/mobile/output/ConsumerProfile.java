package com.jdc.consumer.api.mobile.output;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jdc.consumer.model.entity.Account;
import com.jdc.consumer.model.entity.AccountAccess;
import com.jdc.consumer.model.entity.AccountLockStatus;
import com.jdc.consumer.model.entity.Account.RegistrationType;
import com.jdc.consumer.model.entity.Account.Status;

/** Consumer account details returned to the account owner. */
public record ConsumerProfile(
		UUID id,
		String name,
		String email,
		String phone,
		String address,
		LocalDate dateOfBirth,
		RegistrationType registrationType,
		Status status,
		Instant registeredAt,
		Instant approvedAt,
		AccountLockStatus.Status lockStatus,
		AccountAccess.Status accessStatus) {

	/** Status values are null when their corresponding current-state record is absent. */
	public static ConsumerProfile from(Account account) {
		var lock = account.getLockStatus();
		var access = account.getAccess();
		return new ConsumerProfile(
				account.getId(),
				account.getName(),
				account.getEmail(),
				account.getPhone(),
				account.getAddress(),
				account.getDateOfBirth(),
				account.getRegistrationType(),
				account.getStatus(),
				account.getRegisteredAt(),
				account.getApprovedAt(),
				lock == null ? null : lock.getStatus(),
				access == null ? null : access.getStatus());
	}
}
