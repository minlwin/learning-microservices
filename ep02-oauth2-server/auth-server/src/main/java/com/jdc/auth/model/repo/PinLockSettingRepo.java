package com.jdc.auth.model.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.PinLockSetting;

public interface PinLockSettingRepo extends JpaRepository<PinLockSetting, String> {
    Optional<PinLockSetting> findBySystemAndDeletedFalse(String system);
}
