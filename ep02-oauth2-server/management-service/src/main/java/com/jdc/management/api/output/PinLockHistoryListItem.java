package com.jdc.management.api.output;

import java.time.Instant;
import java.util.UUID;

import com.jdc.management.model.entity.PinLockHistory.LockType;

public record PinLockHistoryListItem(
        UUID id,
        UUID accountId,
        LockType lockType,
        Instant lockAt,
        Instant lockedUntil,
        String requestedBy,
        String name,
        String email,
        String phone) {

}
