package com.jdc.auth.event;

import java.time.Instant;
import java.util.UUID;

public record PinLockSettingChangedEvent(
        int schemaVersion,
        UUID eventId,
        String system,
        long version,
        int tempLockFailsCount,
        int tempLockWaitMinutes,
        int permanentLockFailsCount,
        boolean deleted,
        Instant changedAt) {
}
