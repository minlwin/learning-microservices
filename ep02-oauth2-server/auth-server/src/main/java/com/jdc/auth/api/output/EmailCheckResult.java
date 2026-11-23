package com.jdc.auth.api.output;

/** Identity and system-specific profile state for a trusted subsystem check. */
public record EmailCheckResult(
		boolean identityExists,
		boolean profileExists,
		String profileStatus) {
}
