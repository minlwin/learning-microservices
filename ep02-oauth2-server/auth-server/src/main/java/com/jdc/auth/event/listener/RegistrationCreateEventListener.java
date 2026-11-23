package com.jdc.auth.event.listener;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationCreateEventListener {

	// TODO: Consume ConsumerRegisteredByManagement events from the Consumer service.
	// Deduplicate by event ID and correlate by consumer account ID, email, and system.
	// Create a Consumer registration/invitation record, then send the consumer a
	// verification link to verify their email and initialize their profile/password.
	// Do not create a Management-system profile merely because management initiated it.
	// Recheck (identity, system) uniqueness when completing initialization; an identity
	// already used by another system can be reused. Never overwrite existing credentials.
	// Deliver invitation mail reliably after commit and handle delivery failures/retries.
	// Define invitation expiry, single-use link validation, and the approved-account
	// activation decision separately from the existing immediate-OTP signup flow.

}
