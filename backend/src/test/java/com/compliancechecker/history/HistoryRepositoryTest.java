package com.compliancechecker.history;

import com.compliancechecker.history.HistoryEntry.Finding;
import com.compliancechecker.model.Severity;
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
	void storesFindingsAndEmptyText() {
		repository.save(new HistoryEntry(Instant.parse("2026-11-21T08:00:00Z"), false,
				List.of(new Finding("R-01", Severity.CRITICAL), new Finding("R-09", Severity.MEDIUM)), ""));
		repository.save(new HistoryEntry(Instant.parse("2026-11-21T09:00:00Z"), true, List.of(), ""));

		List<HistoryEntry> entries = repository.findTop100ByOrderByCreatedAtDescIdDesc();

		assertThat(entries.get(0).getFindings()).isEmpty();
		assertThat(entries.get(1).getFindings())
				.containsExactly(new Finding("R-01", Severity.CRITICAL), new Finding("R-09", Severity.MEDIUM));
		assertThat(entries.get(1).getImprovedVersion()).isEmpty();
	}

	private static HistoryEntry entry(String createdAt) {
		return new HistoryEntry(Instant.parse(createdAt), false, List.of(new Finding("R-02", Severity.CRITICAL)),
				"text");
	}
}
