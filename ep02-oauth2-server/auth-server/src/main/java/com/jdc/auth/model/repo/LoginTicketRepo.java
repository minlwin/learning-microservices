package com.jdc.auth.model.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.jdc.auth.model.entity.LoginTicket;

public interface LoginTicketRepo extends JpaRepository<LoginTicket, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<LoginTicket> findByTokenHash(String tokenHash);
}
