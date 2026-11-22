package com.jdc.auth.api.output;

/** True when a profile exists for the email and system, regardless of status. */
public record EmailCheckResult(boolean exists) {
}
