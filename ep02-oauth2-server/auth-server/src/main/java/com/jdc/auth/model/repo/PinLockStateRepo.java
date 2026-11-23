package com.jdc.auth.model.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.PinLockState;

public interface PinLockStateRepo extends JpaRepository<PinLockState, UUID> {
    Optional<PinLockState> findByProfileId(UUID profileId);
}
