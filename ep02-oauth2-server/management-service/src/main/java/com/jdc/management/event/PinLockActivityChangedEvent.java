package com.jdc.management.event;

import java.time.Instant;
import java.util.UUID;

public record PinLockActivityChangedEvent(
        int schemaVersion,
        UUID eventId,
        UUID profileId,
        String username,
        String system,
        String action,
        int failedAttemptCount,
        Instant lockedUntil,
        long settingVersion,
        Instant changedAt,
        String requestedBy) {
}
