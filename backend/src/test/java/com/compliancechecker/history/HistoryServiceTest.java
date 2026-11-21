package com.compliancechecker.history;

import com.compliancechecker.history.HistoryEntry.Finding;
import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.Problem;
import com.compliancechecker.model.Severity;
import com.compliancechecker.service.SensitiveDataScanner;
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
	private static final String EMAIL = "erika.mustermann@example.com";

	private final HistoryRepository repository = mock(HistoryRepository.class);
	private final HistoryService historyService =
			new HistoryService(repository, new SensitiveDataScanner(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void storesTimeResultFindingsAndSanitisedVersion() {
		historyService.record("Reply to " + EMAIL, unsafe(EMAIL, "Reply to [EMAIL_1]"));

		HistoryEntry saved = savedEntry();
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.isSafe()).isFalse();
		assertThat(saved.getFindings()).containsExactly(new Finding("R-01", Severity.CRITICAL));
		assertThat(saved.getImprovedVersion()).isEqualTo("Reply to [EMAIL_1]");
	}

	@Test
	void storesOnlySafeLookingRuleIds() {
		// An LLM could put free text into ruleId; only id-like values are stored.
		Problem odd = new Problem("Customer Erika Mustermann", Severity.HIGH, "Contains a name.", "");
		historyService.record("prompt", new CheckResponse(List.of(odd), ""));

		assertThat(savedEntry().getFindings()).containsExactly(new Finding(Problem.UNSPECIFIED_RULE, Severity.HIGH));
	}

	@Test
	void doesNotStoreTextIdenticalToOriginalPrompt() {
		String prompt = "Explain ETFs in simple words.";
		Problem notice = new Problem("R-10", Severity.LOW, "Just a notice.", "");
		historyService.record(prompt, new CheckResponse(List.of(notice), prompt));

		HistoryEntry saved = savedEntry();
		assertThat(saved.isSafe()).isTrue();
		assertThat(saved.getImprovedVersion()).isEmpty();
	}

	@Test
	void doesNotStoreSanitisedVersionThatStillContainsAMatchedPassage() {
		// e.g. an LLM that listed the name as a problem but forgot to replace it
		Problem name = new Problem("R-01", Severity.CRITICAL, "Contains a customer name.", "Erika Mustermann");
		historyService.record("Write to Erika Mustermann", new CheckResponse(List.of(name), "Write to Erika Mustermann!"));

		assertThat(savedEntry().getImprovedVersion()).isEmpty();
	}

	@Test
	void doesNotStoreSanitisedVersionThatStillContainsDetectableData() {
		// e.g. an LLM that replaced the name but kept the phone number without reporting it
		historyService.record("Call Erika Mustermann at +49 30 1234567",
				unsafe("Erika Mustermann", "Call [CUSTOMER_NAME_1] at +49 30 1234567"));

		assertThat(savedEntry().getImprovedVersion()).isEmpty();
	}

	@Test
	void doesNotStoreOversizedText() {
		String tooLong = "x".repeat(HistoryEntry.MAX_IMPROVED_VERSION_LENGTH + 1);
		historyService.record("original", unsafe(EMAIL, tooLong));

		assertThat(savedEntry().getImprovedVersion()).isEmpty();
	}

	@Test
	void databaseFailureDoesNotFailTheCheck() {
		given(repository.save(any())).willThrow(new DataAccessResourceFailureException("db down"));

		assertThatNoException().isThrownBy(() -> historyService.record("prompt", unsafe(EMAIL, "[EMAIL_1]")));
	}

	private static CheckResponse unsafe(String location, String improvedVersion) {
		return new CheckResponse(List.of(new Problem("R-01", Severity.CRITICAL, "Contains personal data.", location)),
				improvedVersion);
	}

	private HistoryEntry savedEntry() {
		ArgumentCaptor<HistoryEntry> captor = ArgumentCaptor.forClass(HistoryEntry.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}
}
