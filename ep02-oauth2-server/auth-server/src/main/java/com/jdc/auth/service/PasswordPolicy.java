package com.jdc.auth.service;

import java.nio.charset.StandardCharsets;

import com.jdc.auth.exceptions.BusinessRuleViolationException;

public final class PasswordPolicy {

    private static final int MINIMUM_CHARACTERS = 12;
    private static final int MAXIMUM_BCRYPT_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MINIMUM_CHARACTERS
                || password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_BYTES) {
            throw new BusinessRuleViolationException(
                    "Password must contain at least 12 characters and no more than 72 UTF-8 bytes.");
        }
    }
}
