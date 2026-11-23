package com.jdc.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jdc.auth.event.PinLockSettingChangedEvent;
import com.jdc.auth.model.entity.PinLockSetting;
import com.jdc.auth.model.repo.PinLockSettingRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinLockSettingService {

    private final PinLockSettingRepo repository;

    public Optional<PinLockSetting> getSetting(String system) {
        return repository.findBySystemAndDeletedFalse(system);
    }

    @Transactional
    public void apply(PinLockSettingChangedEvent event) {
        validate(event);

        var storedSetting = repository.findById(event.system());
        if (storedSetting.isPresent() && storedSetting.get().getVersion() >= event.version()) {
            return;
        }

        var setting = storedSetting.orElseGet(PinLockSetting::new);

        setting.setSystem(event.system());
        setting.setTempLockFailsCount(event.tempLockFailsCount());
        setting.setTempLockWaitMinutes(event.tempLockWaitMinutes());
        setting.setPermanentLockFailsCount(event.permanentLockFailsCount());
        setting.setDeleted(event.deleted());
        setting.setVersion(event.version());
        repository.save(setting);
    }

    private void validate(PinLockSettingChangedEvent event) {
        if (event.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported PIN lock event schema version.");
        }
        if (event.eventId() == null || event.system() == null || event.system().isBlank()) {
            throw new IllegalArgumentException("PIN lock event ID and system are required.");
        }
        if (event.version() < 0 || event.tempLockFailsCount() < 1 || event.tempLockWaitMinutes() < 1
                || event.permanentLockFailsCount() <= event.tempLockFailsCount()) {
            throw new IllegalArgumentException("PIN lock event contains invalid setting values.");
        }
    }
}
