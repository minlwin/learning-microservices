package com.jdc.auth.security;

import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.props.AuthenticationProps;

/** Validates client system and the roles permitted by configuration. */
public final class ProfileAccess {
	private ProfileAccess() {
	}

	public static boolean allows(String clientSystem, Profile profile, AuthenticationProps properties) {
		return clientSystem != null && clientSystem.equals(profile.getSystem())
				&& properties.allows(clientSystem, profile.getRole());
	}
}
