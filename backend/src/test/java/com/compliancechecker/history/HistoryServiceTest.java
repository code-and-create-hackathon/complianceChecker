package com.compliancechecker.history;

import com.compliancechecker.model.CheckResponse;
import com.compliancechecker.model.ProblemCategory;
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

	private final HistoryRepository repository = mock(HistoryRepository.class);
	private final HistoryService historyService =
			new HistoryService(repository, new SensitiveDataScanner(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void storesTimeResultCategoriesAndSanitizedVersion() {
		historyService.record("Reply to erika.mustermann@example.com", unsafe("Reply to [EMAIL]"));

		HistoryEntry saved = savedEntry();
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.isSafe()).isFalse();
		assertThat(saved.getCategories()).containsExactly(ProblemCategory.CUSTOMER_DATA);
		assertThat(saved.getImprovedVersion()).isEqualTo("Reply to [EMAIL]");
	}

	@Test
	void doesNotStoreTextIdenticalToOriginalPrompt() {
		String prompt = "Explain ETFs in simple words.";
		historyService.record(prompt, new CheckResponse(true, List.of(), List.of(), prompt, true));

		HistoryEntry saved = savedEntry();
		assertThat(saved.isSafe()).isTrue();
		assertThat(saved.getImprovedVersion()).isNull();
	}

	@Test
	void doesNotStoreSanitizedVersionThatStillContainsSensitiveData() {
		// e.g. an LLM that replaced the name but kept the phone number
		historyService.record("Call Erika Mustermann at +49 30 1234567",
				unsafe("Call [CUSTOMER_NAME] at +49 30 1234567"));

		HistoryEntry saved = savedEntry();
		assertThat(saved.getImprovedVersion()).isNull();
		assertThat(saved.getCategories()).containsExactly(ProblemCategory.CUSTOMER_DATA);
	}

	@Test
	void doesNotStoreOversizedText() {
		String tooLong = "x".repeat(HistoryEntry.MAX_IMPROVED_VERSION_LENGTH + 1);
		historyService.record("original", unsafe(tooLong));

		assertThat(savedEntry().getImprovedVersion()).isNull();
	}

	@Test
	void databaseFailureDoesNotFailTheCheck() {
		given(repository.save(any())).willThrow(new DataAccessResourceFailureException("db down"));

		assertThatNoException().isThrownBy(() -> historyService.record("prompt", unsafe("[EMAIL]")));
	}

	private static CheckResponse unsafe(String improvedVersion) {
		return new CheckResponse(false, List.of("Contains an email address."), List.of(ProblemCategory.CUSTOMER_DATA),
				improvedVersion, true);
	}

	private HistoryEntry savedEntry() {
		ArgumentCaptor<HistoryEntry> captor = ArgumentCaptor.forClass(HistoryEntry.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}
}
