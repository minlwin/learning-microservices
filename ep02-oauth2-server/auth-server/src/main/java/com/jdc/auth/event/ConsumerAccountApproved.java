package com.jdc.auth.event;

import java.util.UUID;

public record ConsumerAccountApproved(
		UUID eventId,
		UUID consumerAccountId,
		String email,
		String system,
		String registrationType) {
}
