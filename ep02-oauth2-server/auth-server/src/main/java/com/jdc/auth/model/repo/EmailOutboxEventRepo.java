package com.jdc.auth.model.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.EmailOutboxEvent;

public interface EmailOutboxEventRepo extends JpaRepository<EmailOutboxEvent, UUID> {
	List<EmailOutboxEvent> findTop100BySentAtIsNullOrderByCreatedAtAsc();
}
