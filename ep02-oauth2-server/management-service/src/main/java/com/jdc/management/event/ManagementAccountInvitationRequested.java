package com.jdc.management.event;

import java.time.Instant;
import java.util.UUID;

public record ManagementAccountInvitationRequested(
        int schemaVersion,
        UUID eventId,
        UUID accountId,
        String email,
        Instant requestedAt) {
}
