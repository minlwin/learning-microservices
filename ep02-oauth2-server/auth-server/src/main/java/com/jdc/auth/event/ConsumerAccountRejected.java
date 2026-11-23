package com.jdc.auth.event;

import java.util.UUID;

public record ConsumerAccountRejected(UUID eventId, UUID consumerAccountId, String email, String system,
		String registrationType) {
}
