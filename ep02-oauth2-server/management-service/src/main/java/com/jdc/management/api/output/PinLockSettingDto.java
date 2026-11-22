package com.jdc.management.api.output;

import com.jdc.management.model.entity.PinLockSetting;

public record PinLockSettingDto(
        String system,
        int tempLockFailsCount,
        int tempLockWaitMinutes,
        int permanentLockFailsCount) {

    public static PinLockSettingDto from(PinLockSetting entity) {
        return new PinLockSettingDto(entity.getSystem(), entity.getTempLockFailsCount(),
                entity.getTempLockWailtMinutes(), entity.getPermenantLockFailsCount());
    }
}
