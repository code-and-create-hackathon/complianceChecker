package com.compliancechecker.history;

import com.compliancechecker.model.ProblemCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against an in-memory H2 database. */
@DataJpaTest
class HistoryRepositoryTest {

	@Autowired
	private HistoryRepository repository;

	@Test
	void returnsNewestFirst() {
		repository.save(entry("2026-11-21T08:00:00Z"));
		repository.save(entry("2026-11-21T10:00:00Z"));
		repository.save(entry("2026-11-21T09:00:00Z"));

		List<Instant> times = repository.findTop100ByOrderByCreatedAtDescIdDesc().stream()
				.map(HistoryEntry::getCreatedAt)
				.toList();

		assertThat(times).containsExactly(Instant.parse("2026-11-21T10:00:00Z"),
				Instant.parse("2026-11-21T09:00:00Z"), Instant.parse("2026-11-21T08:00:00Z"));
	}

	@Test
	void storesCategoriesAndNullableText() {
		repository.save(new HistoryEntry(Instant.parse("2026-11-21T08:00:00Z"), false,
				List.of(ProblemCategory.CUSTOMER_DATA, ProblemCategory.ACCOUNT_DATA), null));
		repository.save(new HistoryEntry(Instant.parse("2026-11-21T09:00:00Z"), true, List.of(), null));

		List<HistoryEntry> entries = repository.findTop100ByOrderByCreatedAtDescIdDesc();

		assertThat(entries.get(0).getCategories()).isEmpty();
		assertThat(entries.get(1).getCategories())
				.containsExactly(ProblemCategory.CUSTOMER_DATA, ProblemCategory.ACCOUNT_DATA);
		assertThat(entries.get(1).getImprovedVersion()).isNull();
	}

	private static HistoryEntry entry(String createdAt) {
		return new HistoryEntry(Instant.parse(createdAt), false, List.of(ProblemCategory.OTHER), "text");
	}
}
