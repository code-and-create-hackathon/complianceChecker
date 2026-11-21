package com.compliancechecker.history;

import com.compliancechecker.history.HistoryEntry.Finding;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Unit tests for what gets stored. All test data is made up. */
class HistoryServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

	private final HistoryRepository repository = mock(HistoryRepository.class);
	private final HistoryService historyService = new HistoryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void storesTimeResultAndFindingsOnly() {
		Problem problem = new Problem("R-01", Severity.CRITICAL, "Contains an email.", "erika.mustermann@example.com");

		historyService.record(new CheckResponse(List.of(problem), "Reply to [EMAIL_1]"), "erika");

		HistoryEntry saved = savedEntry();
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.isSafe()).isFalse();
		assertThat(saved.getFindings()).containsExactly(new Finding("R-01", Severity.CRITICAL));
	}

	@Test
	void storesNoTextAtAll() {
		Problem problem = new Problem("R-01", Severity.CRITICAL, "Contains a name.", "Erika Mustermann");

		historyService.record(new CheckResponse(List.of(problem), "Write to [NAME_1] about Erika's loan"), "erika");

		// The entity has no field for any text; its string form must not contain any either.
		assertThat(savedEntry().toString()).doesNotContain("Mustermann").doesNotContain("Write to");
		assertThat(HistoryEntry.class.getDeclaredFields()).extracting("name")
				.doesNotContain("improvedVersion", "prompt", "location", "message");
	}

	@Test
	void storesOnlySafeLookingRuleIds() {
		// An LLM could put free text into ruleId; only id-like values are stored.
		Problem odd = new Problem("Customer Erika Mustermann", Severity.HIGH, "Contains a name.", "");

		historyService.record(new CheckResponse(List.of(odd), ""), "erika");

		assertThat(savedEntry().getFindings()).containsExactly(new Finding(Problem.UNSPECIFIED_RULE, Severity.HIGH));
	}

	@Test
	void lowSeverityNoticeIsSafe() {
		Problem notice = new Problem("R-10", Severity.LOW, "Just a notice.", "");

		historyService.record(new CheckResponse(List.of(notice), ""), "erika");

		assertThat(savedEntry().isSafe()).isTrue();
	}

	@Test
	void databaseFailureDoesNotFailTheCheck() {
		given(repository.save(any())).willThrow(new DataAccessResourceFailureException("db down"));

		assertThatNoException().isThrownBy(() -> historyService.record(new CheckResponse(List.of(), ""), "erika"));
	}

	private HistoryEntry savedEntry() {
		ArgumentCaptor<HistoryEntry> captor = ArgumentCaptor.forClass(HistoryEntry.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}
}
