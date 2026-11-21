package com.compliancechecker.account;

import com.compliancechecker.model.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Users are created by the administrator only; there is no self-registration. */
@Service
public class UserService {

	static final int MAX_USERNAME_LENGTH = 40;

	/** Lower-case letters, digits and . _ - so names are safe in logs, URLs and HTML. */
	public static final String USERNAME_PATTERN = "[a-z0-9._-]{2," + MAX_USERNAME_LENGTH + "}";

	private final UserRepository repository;
	private final Clock clock;

	@Autowired
	public UserService(UserRepository repository) {
		this(repository, Clock.systemUTC());
	}

	UserService(UserRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** "  Anna " and "anna" are the same user. */
	public static String normalize(String username) {
		return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
	}

	public List<AppUser> list() {
		return repository.findAllByOrderByUsernameAsc();
	}

	/** @throws ApiException 409 if the name is taken */
	public AppUser create(String username, Role role) {
		String name = normalize(username);
		if (repository.existsByUsername(name)) {
			throw usernameTaken();
		}
		try {
			return repository.save(new AppUser(name, role, Instant.now(clock)));
		}
		catch (DataIntegrityViolationException ex) {
			// Created by a parallel request between the check above and the insert.
			throw usernameTaken();
		}
	}

	/** Creates the user unless a user with that name exists. Returns true if it was created. */
	public boolean createIfAbsent(String username, Role role) {
		if (repository.existsByUsername(normalize(username))) {
			return false;
		}
		create(username, role);
		return true;
	}

	private static ApiException usernameTaken() {
		return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "This username already exists.");
	}
}
