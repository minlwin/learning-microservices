package com.jdc.consumer.model.repo;

import java.util.UUID;

import com.jdc.consumer.model.BaseRepository;
import com.jdc.consumer.model.entity.PinLockHistory;

public interface PinLockHistoryRepo extends BaseRepository<PinLockHistory, UUID>{
    boolean existsByEventId(UUID eventId);

}
