package com.jdc.management.model.repo;

import java.util.UUID;
import java.util.Optional;
import com.jdc.management.model.BaseRepository;
import com.jdc.management.model.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepo extends BaseRepository<Account, UUID> {
    boolean existsByEmail(String email);
    Optional<Account> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :id")
    Optional<Account> findForUpdate(@Param("id") UUID id);
}
