package com.jdc.auth.model.repo;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.EmailOutboxEvent;

public interface EmailOutboxEventRepo extends JpaRepository<EmailOutboxEvent, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select event from EmailOutboxEvent event where event.sentAt is null "
			+ "and event.deadLetteredAt is null and event.nextAttemptAt <= :now "
			+ "and (event.claimedUntil is null or event.claimedUntil <= :now) order by event.createdAt")
	List<EmailOutboxEvent> lockAvailable(@Param("now") Instant now, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select event from EmailOutboxEvent event where event.id = :id")
	java.util.Optional<EmailOutboxEvent> findForUpdate(@Param("id") UUID id);
}
