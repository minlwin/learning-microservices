package com.jdc.consumer.api.management.output;

import java.time.Instant;
import java.util.UUID;

import com.jdc.consumer.model.entity.Account.RegistrationType;
import com.jdc.consumer.model.entity.Account.Status;

public record ConsumerAccountListItem(
		UUID id,
		String name,
		String phone,
		String email,
		Status status,
		RegistrationType registrationType,
		Instant registeredAt,
		Instant approvedAt
) {

}
