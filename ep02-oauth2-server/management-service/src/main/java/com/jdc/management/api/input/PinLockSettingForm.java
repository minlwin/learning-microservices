package com.jdc.management.api.input;

import jakarta.validation.constraints.Min;

public record PinLockSettingForm(
        @Min(value = 1, message = "Temporary lock failure count must be at least 1.") int tempLockFailsCount,
        @Min(value = 1, message = "Temporary lock duration must be at least 1 minute.") int tempLockWaitMinutes,
        @Min(value = 1, message = "Permanent lock failure count must be at least 1.") int permanentLockFailsCount) {
}
