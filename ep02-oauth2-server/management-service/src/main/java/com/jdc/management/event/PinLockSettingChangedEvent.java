package com.jdc.management.event;

import java.time.Instant;
import java.util.UUID;

import com.jdc.management.model.entity.PinLockSetting;

public record PinLockSettingChangedEvent(
        int schemaVersion,
        UUID eventId,
        String system,
        long version,
        int tempLockFailsCount,
        int tempLockWaitMinutes,
        int permanentLockFailsCount,
        boolean deleted,
        Instant changedAt) {

    public static PinLockSettingChangedEvent from(PinLockSetting setting) {
        return new PinLockSettingChangedEvent(
                1,
                UUID.randomUUID(),
                setting.getSystem(),
                setting.getVersion(),
                setting.getTempLockFailsCount(),
                setting.getTempLockWailtMinutes(),
                setting.getPermenantLockFailsCount(),
                setting.isDeleted(),
                Instant.now());
    }
}
