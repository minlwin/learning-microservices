package com.jdc.management.model.repo;

import java.util.UUID;

import com.jdc.management.model.BaseRepository;
import com.jdc.management.model.entity.PinLockHistory;

public interface PinLockHistoryRepo extends BaseRepository<PinLockHistory, UUID> {
    boolean existsByEventId(UUID eventId);
}
