package com.jdc.management.api.output;

import java.time.Instant;
import java.util.UUID;
import com.jdc.management.model.entity.Account.Status;

public record AccountListItem(UUID id, String name, String phone, String email,
        Status status, Instant registeredAt, Instant activatedAt) {
}
