package com.jdc.consumer.model.repo;

import java.util.List;
import java.util.UUID;

import com.jdc.consumer.model.BaseRepository;
import com.jdc.consumer.model.entity.OutboxEvent;

public interface OutboxEventRepo extends BaseRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
