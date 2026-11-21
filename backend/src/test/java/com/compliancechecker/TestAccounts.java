package com.compliancechecker;

import com.compliancechecker.account.AppUser;
import com.compliancechecker.account.AuthService;
import com.compliancechecker.account.Role;
import com.compliancechecker.account.UserRepository;
import com.compliancechecker.account.UserService;
import com.compliancechecker.policy.PolicyService;

/** Test setup for integration tests that share one Spring context and one in-memory database. All data is made up. */
public final class TestAccounts {

	/** Same as auth.admin-code in src/test/resources/application.properties. */
	public static final String ADMIN_CODE = "test-admin-code";
	/** The administrator AdminBootstrap creates (auth.admin-username default). */
	public static final String ADMIN = "admin";

	private TestAccounts() {
	}

	/** Creates the worker if needed, logs them in and returns the Authorization header value. */
	public static String workerAuthorization(UserService users, AuthService auth, String username) {
		users.createIfAbsent(username, Role.WORKER);
		return "Bearer " + auth.login(username, null).token();
	}

	public static String adminAuthorization(AuthService auth) {
		return "Bearer " + auth.login(ADMIN, ADMIN_CODE).token();
	}

	/** Publishes {@code text} as a new policy version by the administrator. */
	public static void publishPolicy(PolicyService policies, UserRepository users, String text) {
		AppUser admin = users.findByUsername(ADMIN).orElseThrow();
		policies.publish(text, admin);
	}
}
