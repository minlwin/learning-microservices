package com.jdc.management.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.jdc.management.model.BaseRepository;
import com.jdc.management.model.entity.OutboxEvent;

public interface OutboxEventRepo extends BaseRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.publishedAt is null "
            + "and event.deadLetteredAt is null and event.nextAttemptAt <= :now "
            + "and (event.claimedUntil is null or event.claimedUntil <= :now) order by event.createdAt")
    List<OutboxEvent> lockAvailable(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> findForUpdate(@Param("id") UUID id);
}
