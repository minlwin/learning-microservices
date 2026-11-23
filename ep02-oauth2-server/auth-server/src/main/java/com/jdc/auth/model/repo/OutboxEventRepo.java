package com.jdc.auth.model.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.OutboxEvent;

public interface OutboxEventRepo extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
