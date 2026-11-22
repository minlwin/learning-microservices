package com.jdc.consumer.model.repo;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.jdc.consumer.model.BaseRepository;
import com.jdc.consumer.model.entity.AccountLockStatus;

public interface AccountLockStatusRepo extends BaseRepository<AccountLockStatus, UUID>{
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from AccountLockStatus s where s.id = :id")
	Optional<AccountLockStatus> findForUpdate(@Param("id") UUID id);
}
