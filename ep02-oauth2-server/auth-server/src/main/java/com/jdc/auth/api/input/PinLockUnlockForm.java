package com.jdc.auth.api.input;

import jakarta.validation.constraints.NotBlank;

public record PinLockUnlockForm(
        @NotBlank(message = "Choose the profile system.") String system,
        @NotBlank(message = "Enter the profile username.") String username,
        @NotBlank(message = "The management actor is required.") String requestedBy) {
}
