package com.compliancechecker.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the one administrator ({@code ADMIN_USERNAME}) at startup. This is the only way an admin comes to exist. */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserService userService;
	private final AuthService authService;
	private final String adminUsername;

	public AdminBootstrap(UserService userService, AuthService authService,
			@Value("${auth.admin-username:admin}") String adminUsername) {
		this.userService = userService;
		this.authService = authService;
		this.adminUsername = adminUsername;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userService.createIfAbsent(adminUsername, Role.ADMIN)) {
			log.info("administrator account created");
		}
		log.warn("DEMO AUTHENTICATION: workers log in without a password. Do not use with real data.");
		if (!authService.isAdminCodeConfigured()) {
			log.warn("ADMIN_CODE is not set: the administrator cannot log in.");
		}
	}
}
