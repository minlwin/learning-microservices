package com.jdc.auth.event;

import java.time.Instant;
import java.util.UUID;

public record ManagementAccountActivated(
        int schemaVersion,
        UUID eventId,
        UUID accountId,
        String email,
        Instant activatedAt) {
}
