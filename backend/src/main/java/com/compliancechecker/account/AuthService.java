package com.compliancechecker.account;

import com.compliancechecker.model.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEMO AUTHENTICATION. Login hands out a random bearer token that identifies the user on every later request.
 *
 * <ul>
 * <li>Workers log in with their username only, without a password.</li>
 * <li>The administrator additionally needs the shared admin code from the {@code ADMIN_CODE} environment variable.</li>
 * </ul>
 *
 * TODO(security): replace the login step with the company's identity provider (e.g. Amazon Cognito / SSO) before any
 * real use. Everything after the login (tokens, role checks in {@link AuthInterceptor}) stays as it is.
 * TODO(security): sessions live in memory: a restart logs everyone out and several instances do not share them.
 * TODO(security): no rate limit on admin login attempts; use a long random ADMIN_CODE.
 */
@Service
public class AuthService {

	public record Login(String token, AppUser user) {
	}

	private record Session(long userId, Instant expiresAt) {
	}

	private final Map<String, Session> sessions = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	private final UserRepository users;
	private final byte[] adminCode;
	private final Duration sessionDuration;
	private final Clock clock;

	@Autowired
	public AuthService(UserRepository users, @Value("${auth.admin-code:}") String adminCode,
			@Value("${auth.session-hours:12}") long sessionHours) {
		this(users, adminCode, Duration.ofHours(sessionHours), Clock.systemUTC());
	}

	AuthService(UserRepository users, String adminCode, Duration sessionDuration, Clock clock) {
		this.users = users;
		this.adminCode = adminCode == null || adminCode.isBlank() ? null : adminCode.getBytes(StandardCharsets.UTF_8);
		this.sessionDuration = sessionDuration;
		this.clock = clock;
	}

	public boolean isAdminCodeConfigured() {
		return adminCode != null;
	}

	/** @throws ApiException 401 for an unknown user, or an admin without the right code (same answer for both) */
	public Login login(String username, String code) {
		// TODO(security): no password check for workers, demo only.
		AppUser user = users.findByUsername(UserService.normalize(username)).orElseThrow(AuthService::loginFailed);
		if (user.isAdmin() && !adminCodeMatches(code)) {
			throw loginFailed();
		}
		String token = newToken();
		sessions.put(token, new Session(user.getId(), Instant.now(clock).plus(sessionDuration)));
		return new Login(token, user);
	}

	/** The user the token belongs to, if the token is known and not expired and the user still exists. */
	public Optional<AppUser> userFor(String token) {
		Session session = token == null ? null : sessions.get(token);
		if (session == null) {
			return Optional.empty();
		}
		if (Instant.now(clock).isAfter(session.expiresAt())) {
			sessions.remove(token);
			return Optional.empty();
		}
		return users.findById(session.userId());
	}

	public void logout(String token) {
		if (token != null) {
			sessions.remove(token);
		}
	}

	private static ApiException loginFailed() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "Unknown user or wrong admin code.");
	}

	/** Constant-time comparison, so the response time does not reveal how much of the code was right. */
	private boolean adminCodeMatches(String code) {
		return adminCode != null && code != null
				&& MessageDigest.isEqual(adminCode, code.getBytes(StandardCharsets.UTF_8));
	}

	/** 256 random bits, URL-safe. */
	private String newToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
