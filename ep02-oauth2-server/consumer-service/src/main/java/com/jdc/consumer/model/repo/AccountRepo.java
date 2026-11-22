package com.jdc.consumer.model.repo;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.jdc.consumer.model.BaseRepository;

import com.jdc.consumer.model.entity.Account;

public interface AccountRepo extends BaseRepository<Account, UUID>{
	Optional<Account> findByEmail(String email);
	boolean existsByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id = :id")
	Optional<Account> findForUpdate(@Param("id") UUID id);
}
