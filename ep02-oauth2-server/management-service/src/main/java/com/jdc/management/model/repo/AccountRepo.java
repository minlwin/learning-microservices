package com.jdc.management.model.repo;

import java.util.UUID;
import java.util.Optional;
import com.jdc.management.model.BaseRepository;
import com.jdc.management.model.entity.Account;

public interface AccountRepo extends BaseRepository<Account, UUID> {
    boolean existsByEmail(String email);
    Optional<Account> findByEmail(String email);
}
