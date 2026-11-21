package com.compliancechecker.account;

import java.time.Instant;

/** JSON shape of a user, see API.md. */
public record UserView(String username, Role role, Instant createdAt) {

	public static UserView of(AppUser user) {
		return new UserView(user.getUsername(), user.getRole(), user.getCreatedAt());
	}
}
