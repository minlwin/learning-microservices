package com.jdc.auth.model.repo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.PinLockHistory;

public interface PinLockHistoryRepo extends JpaRepository<PinLockHistory, UUID> {
}
