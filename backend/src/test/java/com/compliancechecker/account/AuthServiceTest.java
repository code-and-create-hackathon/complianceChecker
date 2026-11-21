package com.compliancechecker.account;

import com.compliancechecker.model.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");

	private final UserRepository users = mock(UserRepository.class);
	private final AppUser erika = withId(new AppUser("erika", Role.WORKER, NOW), 1L);
	private final AppUser admin = withId(new AppUser("admin", Role.ADMIN, NOW), 2L);

	@Test
	void tokenExpiresAfterTheSessionDuration() {
		given(users.findByUsername("erika")).willReturn(Optional.of(erika));
		given(users.findById(anyLong())).willReturn(Optional.of(erika));
		MovableClock clock = new MovableClock(NOW);
		AuthService auth = service("code", clock);

		String token = auth.login("erika", null).token();
		clock.now = NOW.plus(Duration.ofHours(12));
		assertThat(auth.userFor(token)).contains(erika);
		clock.now = NOW.plus(Duration.ofHours(12)).plusSeconds(1);
		assertThat(auth.userFor(token)).isEmpty();
	}

	@Test
	void adminWithoutConfiguredCodeCannotLogInAtAll() {
		given(users.findByUsername("admin")).willReturn(Optional.of(admin));

		AuthService noCode = service("", Clock.systemUTC());
		assertThat(noCode.isAdminCodeConfigured()).isFalse();
		assertThatThrownBy(() -> noCode.login("admin", "")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> noCode.login("admin", null)).isInstanceOf(ApiException.class);
	}

	@Test
	void tokensAreRandomAndLong() {
		given(users.findByUsername("erika")).willReturn(Optional.of(erika));
		AuthService auth = service("code", Clock.systemUTC());

		String first = auth.login("erika", null).token();
		assertThat(first).hasSize(43).isNotEqualTo(auth.login("erika", null).token());
	}

	/** A clock the test can move forward. */
	private static final class MovableClock extends Clock {

		Instant now;

		MovableClock(Instant now) {
			this.now = now;
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	private AuthService service(String adminCode, Clock clock) {
		return new AuthService(users, adminCode, Duration.ofHours(12), clock);
	}

	private static AppUser withId(AppUser user, long id) {
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
