package com.jdc.management.model.repo;

import java.util.List;
import java.util.UUID;

import com.jdc.management.model.BaseRepository;
import com.jdc.management.model.entity.OutboxEvent;

public interface OutboxEventRepo extends BaseRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
