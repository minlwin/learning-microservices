package com.jdc.auth.security;

import com.jdc.auth.model.entity.Profile;

/** Validates client system and the roles permitted for that system. */
public final class ProfileAccess {
	private ProfileAccess() { }

	public static boolean allows(Profile.System clientSystem, Profile profile) {
		if (clientSystem == null || clientSystem != profile.getSystem() || profile.getRole() == null) {
			return false;
		}
		return switch (clientSystem) {
			case Consumer -> profile.getRole() == Profile.Role.Consumer;
			case Management -> profile.getRole() == Profile.Role.Management || profile.getRole() == Profile.Role.Administrator;
		};
	}
}
